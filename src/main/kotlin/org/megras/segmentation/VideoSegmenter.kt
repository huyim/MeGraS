package org.megras.segmentation

import com.github.kokorin.jaffree.StreamType
import com.github.kokorin.jaffree.ffmpeg.ChannelInput
import com.github.kokorin.jaffree.ffmpeg.ChannelOutput
import com.github.kokorin.jaffree.ffmpeg.FFmpeg
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import java.nio.channels.SeekableByteChannel
import java.util.concurrent.TimeUnit


object VideoSegmenter {

    fun segment(videoStream: SeekableByteChannel, segmentation: Segmentation): SeekableInMemoryByteChannel? = try {
        when(segmentation.type) {
            SegmentationType.RECT -> segmentRect(videoStream, segmentation as Rect)
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
            .addOutput(ChannelOutput.toChannel("", out).setFormat("ogg"))
            .execute()
        return out
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
            .addOutput(ChannelOutput.toChannel("", out).setFormat("ogg"))
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
                ffmpeg.addArgument("-an")
                    .addOutput(ChannelOutput.toChannel("", out).setFormat("ogg"))
            }

            "audio" -> {
                ffmpeg.addArguments("-q:a", "0").addArguments("-map", "a")
                    .addOutput(ChannelOutput.toChannel("", out).setFormat("ogg"))
            }
        }

        ffmpeg.execute()
        return out
    }
}