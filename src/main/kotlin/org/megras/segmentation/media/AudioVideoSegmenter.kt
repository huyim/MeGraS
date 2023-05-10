package org.megras.segmentation.media

import com.github.kokorin.jaffree.StreamType
import com.github.kokorin.jaffree.ffmpeg.*
import com.github.kokorin.jaffree.ffprobe.FFprobe
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import org.megras.api.rest.RestErrorStatus
import org.megras.segmentation.type.*
import java.nio.channels.SeekableByteChannel
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong


object AudioVideoSegmenter {

    private const val outputFormat = "webm"

    fun segment(stream: SeekableByteChannel, segmentation: Segmentation): ByteArray? = try {
        when (segmentation) {
            is Rect -> segmentRect(stream, segmentation)
            is TwoDimensionalSegmentation,
            is ThreeDimensionalSegmentation,
            is ColorChannel -> segmentPerFrame(stream, segmentation)
            is StreamChannel -> segmentChannel(stream, segmentation)
            is Time -> segmentTime(stream, segmentation)
            is Frequency -> segmentFrequency(stream, segmentation)
            else -> null
        }
    } catch (e: Exception) {
        //TODO log
        null
    }

    private fun segmentRect(stream: SeekableByteChannel, rect: Rect): ByteArray {
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
        return out.array()
    }

    private fun segmentPerFrame(stream: SeekableByteChannel, segmentation: Segmentation): ByteArray {
        if (!hasStreamType(stream, StreamType.VIDEO)) {
            throw RestErrorStatus.noVideo
        }

        val probe = probeStream(stream)
        val videoProbe = probe.first { s -> s.codecType == StreamType.VIDEO }
        val frameRate = videoProbe.rFrameRate.toInt()

        val totalDuration = AtomicLong()
        FFmpeg.atPath()
            .addInput(ChannelInput.fromChannel(stream))
            .addOutput(NullOutput())
            .setProgressListener { progress -> totalDuration.set(progress.timeMillis) }
            .execute()

        return VideoShapeSegmenter(
            stream,
            segmentation,
            frameRate,
            totalDuration.get(),
            videoProbe.width,
            videoProbe.height
        ).execute()
    }

    /**
     * 2 possible cases:
     * - selection is either "audio" or "video", then discard the other
     * - selection is indices of streams
     */
    private fun segmentChannel(stream: SeekableByteChannel, channel: StreamChannel): ByteArray? {
        val out = SeekableInMemoryByteChannel()
        val ffmpeg = FFmpeg
            .atPath()
            .addInput(ChannelInput.fromChannel(stream))
            .setOverwriteOutput(true)

        // 'audio' or 'video' stream selection
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
        }
        // stream number selection
        else if (channel.selection.all { it.toIntOrNull() != null }){
            channel.selection.forEach { ffmpeg.addArguments("-map", "0:${it}") }
            ffmpeg.addArguments("-c", "copy")
        } else {
            return null
        }

        ffmpeg.addOutput(ChannelOutput.toChannel("", out).setFormat(outputFormat)).execute()

        return out.array()
    }

    private fun segmentFrequency(stream: SeekableByteChannel, frequency: Frequency): ByteArray {
        if (!hasStreamType(stream, StreamType.AUDIO)) {
            throw RestErrorStatus.noAudio
        }

        val out = SeekableInMemoryByteChannel()
        FFmpeg.atPath()
            .addInput(ChannelInput.fromChannel(stream))
            .setOverwriteOutput(true)
            .addArguments("-filter:a", "highpass=f=${frequency.interval.low}, lowpass=f=${frequency.interval.high}")
            .addOutput(ChannelOutput.toChannel("", out).setFormat(outputFormat))
            .execute()

        return out.array()
    }

    private fun segmentTime(stream: SeekableByteChannel, time: Time): ByteArray {
        val out = SeekableInMemoryByteChannel()

        val firstPoint = time.intervals.first().low.toDouble()
        val lastPoint = time.intervals.last().high.toDouble()

        val ffmpeg = FFmpeg.atPath()
                .addInput(ChannelInput.fromChannel(stream).setPosition(firstPoint, TimeUnit.SECONDS)
                        .setDuration(lastPoint - firstPoint, TimeUnit.SECONDS))
                .setOverwriteOutput(true)

        if (time.intervals.size > 1) {
            val discard = time.getIntervalsToDiscard()

            // The video between segments is turned black (need shifting because beginning might be cut away)
            // TODO: figure out how to make it transparent instead of black
            val blackFilters = discard.map { "drawbox=t=fill:c=black:enable='between(t,${it.low - firstPoint},${it.high - firstPoint})'" }
            ffmpeg.addArguments("-vf", blackFilters.joinToString(", "))

            // The audio between segments is muted (need shifting because beginning might be cut away)
            val muteFilters = time.getIntervalsToDiscard().map { "volume=enable='between(t,${it.low - firstPoint},${it.high - firstPoint})':volume=0" }
            ffmpeg.addArguments("-af", muteFilters.joinToString(", "))
        }

        ffmpeg.addOutput(ChannelOutput.toChannel("", out).setFormat(outputFormat))
                .execute()

        return out.array()
    }

    private fun probeStream(stream: SeekableByteChannel): List<com.github.kokorin.jaffree.ffprobe.Stream> {
        val probe = FFprobe.atPath().setShowStreams(true).setInput(stream).execute()
        return probe.streams
    }

    private fun hasStreamType(stream: SeekableByteChannel, type: StreamType): Boolean {
        return probeStream(stream).find { it.codecType == type } != null
    }
}