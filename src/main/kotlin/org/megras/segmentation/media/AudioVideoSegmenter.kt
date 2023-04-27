package org.megras.segmentation.media

import com.github.kokorin.jaffree.StreamType
import com.github.kokorin.jaffree.ffmpeg.*
import com.github.kokorin.jaffree.ffprobe.FFprobe
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import org.megras.api.rest.RestErrorStatus
import org.megras.segmentation.*
import org.megras.segmentation.type.*
import java.nio.channels.SeekableByteChannel
import java.util.concurrent.TimeUnit


object AudioVideoSegmenter {

    private const val outputFormat = "webm"

    fun segment(stream: SeekableByteChannel, segmentation: Segmentation): SeekableInMemoryByteChannel? = try {
        when(segmentation.segmentationType) {
            SegmentationType.RECT -> segmentRect(stream, segmentation as Rect)
            SegmentationType.POLYGON,
            SegmentationType.BEZIER,
            SegmentationType.BSPLINE,
            SegmentationType.PATH,
            SegmentationType.MASK,
            SegmentationType.HILBERT,
            SegmentationType.ROTOSCOPE,
            SegmentationType.MESH -> segmentShape(stream, segmentation)
            SegmentationType.TIME -> segmentTime(stream, segmentation as Time)
            SegmentationType.FREQUENCY -> segmentFrequency(stream, segmentation as Frequency)
            SegmentationType.CHANNEL -> segmentChannel(stream, segmentation as Channel)
            else -> null
        }
    } catch (e: Exception) {
        //TODO log
        null
    }

    private fun segmentRect(stream: SeekableByteChannel, rect: Rect): SeekableInMemoryByteChannel {
        if (!hasStreamType(stream, StreamType.VIDEO)) {
            throw RestErrorStatus.noVideo
        }

        val out = SeekableInMemoryByteChannel()
        FFmpeg.atPath()
            .addInput(ChannelInput.fromChannel(stream))
            .setFilter(StreamType.VIDEO, "crop=w=${rect.width}:h=${rect.height}:x=${rect.xmin}:y=${rect.ymin}")
            .setOverwriteOutput(true)
            .addOutput(ChannelOutput.toChannel("", out).setFormat(outputFormat))
            .execute()
        return out
    }

    private fun segmentShape(stream: SeekableByteChannel, segmentation: Segmentation): SeekableInMemoryByteChannel {
        if (!hasStreamType(stream, StreamType.VIDEO)) {
            throw RestErrorStatus.noVideo
        }

        val videoProbe = probeStream(stream).first { s -> s.codecType == StreamType.VIDEO }
        val frameRate = videoProbe.rFrameRate.toInt()

        val shift = when (segmentation) {
            is Shiftable -> segmentation.getShiftAmount()
            else -> 0.0
        }

        var totalFrames = 0
        if (segmentation is Hilbert) {
            FFmpeg.atPath().addInput(ChannelInput.fromChannel(stream))
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

        return VideoShapeSegmenter(
            stream,
            segmentation,
            frameRate,
            shift,
            totalFrames,
            videoProbe.width,
            videoProbe.height
        ).execute()
    }

    /**
     * 2 possible cases:
     * - selection is either "audio" or "video", then discard the other
     * - selection is indices of streams
     */
    private fun segmentChannel(stream: SeekableByteChannel, channel: Channel): SeekableInMemoryByteChannel {
        val out = SeekableInMemoryByteChannel()
        val ffmpeg = FFmpeg
            .atPath()
            .addInput(ChannelInput.fromChannel(stream))
            .setOverwriteOutput(true)

        if (channel.selection.size == 1 && (channel.selection[0] == "audio" || channel.selection[0] == "video")) {
            if (channel.selection[0] == "video" && !hasStreamType(stream, StreamType.VIDEO)) {
                throw RestErrorStatus.noVideo
            }
            if (channel.selection[0] == "audio" && !hasStreamType(stream, StreamType.AUDIO)) {
                throw RestErrorStatus.noAudio
            }

            when(channel.selection[0]) {
                "video" -> {
                    ffmpeg.addArguments("-c:v", "copy").addArgument("-an")
                }

                "audio" -> {
                    ffmpeg.addArguments("-c:a", "copy").addArgument("-vn")
                }
            }
        } else {
            channel.selection.forEach { ffmpeg.addArguments("-map", "0:${it}") }
            ffmpeg.addArguments("-c", "copy")
        }

        ffmpeg.addOutput(ChannelOutput.toChannel("", out).setFormat(outputFormat)).execute()

        return out
    }

    private fun segmentFrequency(stream: SeekableByteChannel, frequency: Frequency): SeekableInMemoryByteChannel {
        if (!hasStreamType(stream, StreamType.AUDIO)) {
            throw RestErrorStatus.noAudio
        }

        val out = SeekableInMemoryByteChannel()
        FFmpeg.atPath()
            .addInput(ChannelInput.fromChannel(stream))
            .setOverwriteOutput(true)
            .addArguments("-filter:a", "highpass=f=${frequency.low}, lowpass=f=${frequency.high}")
            .addOutput(ChannelOutput.toChannel("", out).setFormat(outputFormat))
            .execute()

        return out
    }

    private fun segmentTime(stream: SeekableByteChannel, time: Time): SeekableInMemoryByteChannel {
        val out = SeekableInMemoryByteChannel()

        if (time.intervals.size == 1) {
            FFmpeg.atPath()
                .addInput(ChannelInput.fromChannel(stream).setPosition(time.intervals[0].low, TimeUnit.SECONDS)
                    .setDuration(time.intervals[0].high - time.intervals[0].low, TimeUnit.SECONDS))
                .setOverwriteOutput(true)
                .addOutput(ChannelOutput.toChannel("", out).setFormat(outputFormat))
                .execute()
        } else {
            val firstPoint = time.intervals.first().low
            val lastPoint = time.intervals.last().high

            val discard = time.getIntervalsToDiscard()

            // The video between segments is turned black (need shifting because beginning might be cut away)
            // TODO: figure out how to make it transparent instead of black
            val blackFilters = discard.map { "drawbox=t=fill:c=black:enable='between(t,${it.low - firstPoint},${it.high - firstPoint})'" }

            // The audio between segments is muted (need shifting because beginning might be cut away)
            val muteFilters = time.getIntervalsToDiscard().map { "volume=enable='between(t,${it.low - firstPoint},${it.high - firstPoint})':volume=0" }

            FFmpeg.atPath()
                .addInput(ChannelInput.fromChannel(stream).setPosition(firstPoint, TimeUnit.SECONDS)
                    .setDuration(lastPoint - firstPoint, TimeUnit.SECONDS))
                .setOverwriteOutput(true)
                .addArguments("-vf", blackFilters.joinToString(", "))
                .addArguments("-af", muteFilters.joinToString(", "))
                .addOutput(ChannelOutput.toChannel("", out).setFormat(outputFormat))
                .execute()
        }

        return out
    }

    private fun probeStream(stream: SeekableByteChannel): List<com.github.kokorin.jaffree.ffprobe.Stream> {
        val probe = FFprobe.atPath().setShowStreams(true).setInput(stream).execute()
        return probe.streams
    }

    private fun hasStreamType(stream: SeekableByteChannel, type: StreamType): Boolean {
        return probeStream(stream).find { it.codecType == type } != null
    }
}