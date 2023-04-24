package org.megras.segmentation.media

import com.github.kokorin.jaffree.LogLevel
import com.github.kokorin.jaffree.ffmpeg.*
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import org.megras.api.rest.RestErrorStatus
import org.megras.segmentation.type.Hilbert
import org.megras.segmentation.type.Rotoscope
import org.megras.segmentation.type.Segmentation
import java.nio.channels.SeekableByteChannel

/**
 * For more information, see the Jaffree mosaic example
 * https://github.com/kokorin/Jaffree/blob/master/src/test/java/examples/MosaicExample.java
 */
class VideoShapeSegmenter(
    val videoStream: SeekableByteChannel,
    val segmentation: Segmentation,
    val frameRate: Int,
    val totalFrames: Int,
    val width: Int,
    val height: Int
) {

    fun execute(): SeekableInMemoryByteChannel {
        val out = SeekableInMemoryByteChannel()

        val frameIterator = FrameIterator()
        val ffmpeg: FFmpeg = FFmpeg.atPath()
            .addInput(ChannelInput.fromChannel(videoStream))
            .addOutput(FrameOutput.withConsumer(frameIterator.consumer).setFrameRate(frameRate))
            .setContextName("input")

        val ffmpegThread = Thread({
            ffmpeg.execute()
        }, "Reader-main")

        ffmpegThread.isDaemon = true
        ffmpegThread.start()

        val frameProducer = createFrameProducer(frameIterator)
        FFmpeg.atPath()
            .addInput(FrameInput.withProducer(frameProducer, ImageFormats.ABGR, 5000L).setFrameRate(frameRate))
            .setOverwriteOutput(true)
            .setLogLevel(LogLevel.TRACE)
            .addArguments("-c:a", "copy")
            .addArguments("-c:v", "libvpx-vp9")
            .addOutput(ChannelOutput.toChannel("", out).setFormat("webm"))
            .setContextName("output")
            .execute()

        return out
    }

    private fun createFrameProducer(frameIterator: FrameIterator): FrameProducer {
        return object : FrameProducer {
            private var frameNumber = 0
            private val videoFrameDuration = (1000 / frameRate).toLong()
            private var timecode: Long = 0
            private var nextVideoFrame: Frame? = null
            private var nextVideoFrameTimecode: Long = 0
            override fun produceStreams(): MutableList<Stream> {
                val streams: MutableList<Stream> = ArrayList()
                streams.add(
                    Stream()
                        .setId(0)
                        .setType(Stream.Type.VIDEO)
                        .setTimebase(1000L)
                        .setWidth(width)
                        .setHeight(height)
                )

                return streams
            }

            override fun produce(): Frame? {
                var result: Frame? = null
                if (nextVideoFrameTimecode <= timecode) {
                    if (nextVideoFrameTimecode == 0L) {
                        readNextVideoFrames(nextVideoFrameTimecode)
                    }
                    result = produceVideoFrame(nextVideoFrame)
                    readNextVideoFrames(nextVideoFrameTimecode)
                }
                timecode = nextVideoFrameTimecode
                return result
            }

            fun produceVideoFrame(videoFrame: Frame?): Frame? {
                if (videoFrame == null) {
                    return null
                }

                var seg: Segmentation? = segmentation
                if (seg is Rotoscope) {
                    seg = seg.interpolate(nextVideoFrameTimecode.toDouble() / 1000)
                }

                if (seg is Hilbert) {
                    seg.relativeTimestamp = frameNumber.toDouble() / totalFrames
                }

                if (seg == null) return null

                val segmentMask = ImageSegmenter.toBinary(videoFrame.image, seg) ?: throw RestErrorStatus.invalidSegmentation
                val segmentedImage = ImageSegmenter.segment(videoFrame.image, segmentMask) ?: throw RestErrorStatus.invalidSegmentation
                val result: Frame = Frame.createVideoFrame(0, nextVideoFrameTimecode, segmentedImage)
                nextVideoFrameTimecode += videoFrameDuration
                frameNumber++
                return result
            }

            private fun readNextVideoFrames(videoTs: Long) {
                if (!frameIterator.hasNext) {
                    nextVideoFrame = null
                }
                while (frameIterator.hasNext) {
                    val frame: Frame = frameIterator.next() ?: break
                    val stream = frameIterator.getStream(frame.getStreamId()) ?: break
                    when (stream.getType()) {
                        Stream.Type.VIDEO -> nextVideoFrame = frame
                        else -> {}
                    }
                    val frameTs: Long = 1000L * frame.getPts() / stream.getTimebase()
                    if (frameTs >= videoTs) {
                        break
                    }
                }
            }
        }
    }

    class FrameIterator : MutableIterator<Frame?> {
        @Volatile
        var hasNext = true

        @Volatile
        private var next: Frame? = null

        @Volatile
        var tracks: List<Stream>? = null
            private set
        val consumer: FrameConsumer = object : FrameConsumer {
            override fun consumeStreams(tracks: MutableList<Stream>) {
                this@FrameIterator.tracks = tracks
            }

            override fun consume(frame: Frame?) {
                while (next != null) {
                    try {
                        Thread.sleep(10)
                    } catch (e: InterruptedException) {
                        //TODO
                    }
                }
                hasNext = frame != null
                next = frame
            }
        }

        override fun hasNext(): Boolean {
            waitForNextFrame()
            return hasNext
        }

        override fun next(): Frame? {
            waitForNextFrame()
            val result: Frame? = next
            next = null
            return result
        }

        override fun remove() {
            throw UnsupportedOperationException("remove")
        }

        fun getStream(id: Int): Stream? {
            for (stream in tracks!!) {
                if (stream.getId() == id) {
                    return stream
                }
            }
            return null
        }

        private fun waitForNextFrame() {
            while (hasNext && next == null) {
                try {
                    Thread.sleep(10)
                } catch (e: InterruptedException) {
                    //TODO
                }
            }
        }
    }
}