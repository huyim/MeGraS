package org.megras.segmentation

import com.github.kokorin.jaffree.StreamType
import com.github.kokorin.jaffree.ffmpeg.*
import com.github.kokorin.jaffree.ffprobe.FFprobe
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import org.megras.api.rest.RestErrorStatus
import java.awt.image.BufferedImage
import java.nio.channels.SeekableByteChannel
import java.util.concurrent.TimeUnit


object VideoSegmenter {

    private val outputFormat = "webm"

    fun segment(videoStream: SeekableByteChannel, segmentation: Segmentation): SeekableInMemoryByteChannel? = try {
        when(segmentation.type) {
            SegmentationType.RECT -> segmentRect(videoStream, segmentation as Rect)
            SegmentationType.POLYGON,
            SegmentationType.BEZIER,
            SegmentationType.BSPLINE,
            SegmentationType.PATH,
            SegmentationType.MASK,
            SegmentationType.HILBERT,
            SegmentationType.ROTOPOLYGON,
            SegmentationType.ROTOBEZIER,
            SegmentationType.ROTOBSPLINE -> segmentShape(videoStream, segmentation)
            SegmentationType.TIME -> segmentTime(videoStream, segmentation as Time)
            SegmentationType.CHANNEL -> segmentChannel(videoStream, segmentation as Channel)
            else -> null
        }
    } catch (e: Exception) {
        //TODO log
        null
    }

    private fun segmentRect(videoStream: SeekableByteChannel, rect: Rect): SeekableInMemoryByteChannel {
        val out = SeekableInMemoryByteChannel()
        FFmpeg.atPath()
            .addInput(ChannelInput.fromChannel(videoStream))
            .setFilter(StreamType.VIDEO, "crop=w=${rect.width}:h=${rect.height}:x=${rect.xmin}:y=${rect.ymin}")
            .setOverwriteOutput(true)
            .addOutput(ChannelOutput.toChannel("", out).setFormat(outputFormat))
            .execute()
        return out
    }

    private fun segmentShape(videoStream: SeekableByteChannel, segmentation: Segmentation): SeekableInMemoryByteChannel {
        val probe = FFprobe.atPath().setShowStreams(true).setInput(videoStream).execute()
        val videoProbe = probe.streams.first { s -> s.codecType == StreamType.VIDEO }
        val frameRate = videoProbe.rFrameRate.toInt()

        var totalFrames = 0
        if (segmentation is Hilbert) {
            FFmpeg.atPath().addInput(ChannelInput.fromChannel(videoStream))
                .addOutput(
                    FrameOutput.withConsumerAlpha(
                        object : FrameConsumer {
                            override fun consumeStreams(streams: List<Stream?>?) {}
                            override fun consume(frame: Frame?) {
                                if (frame != null) {
                                    totalFrames++
                                }
                            }
                        }
                    )
                        .disableStream(StreamType.AUDIO)
                        .disableStream(StreamType.SUBTITLE)
                        .disableStream(StreamType.DATA)
                )
                .execute()
        }

        return VideoShapeSegmenter(videoStream, segmentation, frameRate, totalFrames, videoProbe.width, videoProbe.height).execute()
    }

    private fun segmentTime(videoStream: SeekableByteChannel, time: Time): SeekableInMemoryByteChannel? {

        if (time.intervals.size > 1) {
            return null
        }

        val out = SeekableInMemoryByteChannel()
        FFmpeg.atPath()
            .addInput(ChannelInput.fromChannel(videoStream).setPosition(time.intervals[0].first, TimeUnit.SECONDS)
                .setDuration(time.intervals[0].second - time.intervals[0].first, TimeUnit.SECONDS))
            .setOverwriteOutput(true)
            .addOutput(ChannelOutput.toChannel("", out).setFormat(outputFormat))
            .execute()
        return out
    }

    private fun segmentChannel(videoStream: SeekableByteChannel, channel: Channel): SeekableInMemoryByteChannel? {

        if (channel.selection.size > 1) {
            return null
        }

        val out = SeekableInMemoryByteChannel()

        val ffmpeg = FFmpeg
            .atPath()
            .addInput(ChannelInput.fromChannel(videoStream))
            .setOverwriteOutput(true)

        when(channel.selection[0]) {
            "video" -> {
                ffmpeg.addArguments("-c:v", "copy").addArgument("-an")
                    .addOutput(ChannelOutput.toChannel("", out).setFormat(outputFormat))
            }

            "audio" -> {
                ffmpeg.addArguments("-c:a", "copy").addArgument("-vn")
                    .addOutput(ChannelOutput.toChannel("", out).setFormat(outputFormat))
            }
        }

        ffmpeg.execute()
        return out
    }
}