package org.megras.segmentation.media

import com.github.kokorin.jaffree.StreamType
import com.github.kokorin.jaffree.ffmpeg.*
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import org.megras.api.rest.RestErrorStatus
import org.megras.segmentation.type.*
import java.nio.channels.SeekableByteChannel
import java.util.*
import java.util.concurrent.TimeUnit


/**
 * For more information, see the Jaffree mosaic example
 * https://github.com/kokorin/Jaffree/blob/master/src/test/java/examples/MosaicExample.java
 */
class VideoShapeSegmenter(
    val videoStream: SeekableByteChannel,
    val segmentation: Segmentation,
    val frameRate: Int,
    val totalDuration: Long,
    val width: Int,
    val height: Int
) {

    fun execute(): SeekableInMemoryByteChannel {
        val out = SeekableInMemoryByteChannel()

        val frameIterator = FrameIterator()

        val ffmpegThread = Thread({
            FFmpeg.atPath()
                .addInput(ChannelInput.fromChannel(videoStream))
                .addOutput(
                    FrameOutput.withConsumer(frameIterator.consumer)
                        .setFrameRate(frameRate)
                )
                .setContextName("input")
                .execute()
        }, "Reader-main")

        ffmpegThread.isDaemon = true
        ffmpegThread.start()

        val xBounds = when (segmentation) {
            is TwoDimensionalSegmentation -> segmentation.getXBounds()
            is ThreeDimensionalSegmentation -> segmentation.getXBounds()
            else -> throw RestErrorStatus.invalidSegmentation
        }
        val yBounds = when (segmentation) {
            is TwoDimensionalSegmentation -> segmentation.getYBounds()
            is ThreeDimensionalSegmentation -> segmentation.getYBounds()
            else -> throw RestErrorStatus.invalidSegmentation
        }
        val tBounds = when (segmentation) {
            is ThreeDimensionalSegmentation -> segmentation.getZBounds()
            else -> null
        }

        val frameProducer = createFrameProducer(frameIterator, tBounds?.low ?: 0)

        val ffmpeg = FFmpeg.atPath()
            .addInput(
                FrameInput.withProducer(frameProducer, ImageFormats.ABGR, 5000L)
                    .setFrameRate(frameRate)
            )
        // Optionally add temporal filter
        if (tBounds != null) {
            ffmpeg.addInput(
                ChannelInput.fromChannel(videoStream)
                    .setPosition(tBounds.low, TimeUnit.SECONDS)
                    .setDuration(tBounds.high - tBounds.low, TimeUnit.SECONDS)
            )
        } else {
            ffmpeg.addInput(ChannelInput.fromChannel(videoStream))
        }
        // Optionally crop frames
        ffmpeg
            .setFilter(StreamType.VIDEO, "crop=w=${xBounds.high - xBounds.low}:h=${yBounds.high - yBounds.low}:x=${xBounds.low}:y=${yBounds.low}")
            .setOverwriteOutput(true)
            .addOutput(
                ChannelOutput.toChannel("", out)
                .setFormat("webm")
                .addMap(0, StreamType.VIDEO)
                .addMap(1, StreamType.AUDIO)
            )
            .setContextName("output")
            .execute()

        return out
    }

    private fun createFrameProducer(frameIterator: FrameIterator, shift: Number): FrameProducer {
        return object : FrameProducer {
            private val shiftTimecode = shift.toLong() * 1000
            private val videoFrameDuration = (1000 / frameRate).toLong()
            private var timecode: Long = shiftTimecode
            private var nextVideoFrame: Frame? = null
            private var nextVideoFrameTimecode: Long = shiftTimecode

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
                    if (nextVideoFrameTimecode == shiftTimecode) {
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

                var seg = segmentation
                if (seg is Rotoscope) {
                    seg = seg.interpolate(nextVideoFrameTimecode.toDouble() / 1000) ?: return null
                }
                if (seg is Hilbert) {
                    seg.relativeTimestamp = nextVideoFrameTimecode.toDouble() / totalDuration
                }
                if (seg is MeshBody) {
                    seg = seg.slice(nextVideoFrameTimecode.toFloat() / 1000) ?: return null
                }

                val segmentedImage = ImageSegmenter.segment(videoFrame.image, seg) ?: throw RestErrorStatus.invalidSegmentation
                val result: Frame = Frame.createVideoFrame(0, nextVideoFrameTimecode, segmentedImage)
                nextVideoFrameTimecode += videoFrameDuration
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