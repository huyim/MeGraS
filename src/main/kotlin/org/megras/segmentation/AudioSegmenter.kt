package org.megras.segmentation

import org.megras.data.fs.ObjectStoreResult
import java.io.BufferedInputStream
import java.io.SequenceInputStream
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.math.roundToLong


object AudioSegmenter {

    fun segment(audioStream: ObjectStoreResult, segmentation: Segmentation): AudioInputStream? = try {
        when(segmentation.type) {
            SegmentationType.TIME -> segmentTime(audioStream, segmentation as Time)
            else -> null
        }
    } catch (e: Exception) {
        //TODO log
        null
    }

    private fun segmentTime(inputStream: ObjectStoreResult, time: Time): AudioInputStream {

        var bufferedStream = BufferedInputStream(inputStream.inputStream())
        var audioStream = AudioSystem.getAudioInputStream(bufferedStream)

        val audioFormat = audioStream.format
        val bytesPerSecond = audioFormat.frameSize * audioFormat.frameRate.roundToLong()

        audioStream.skip(time.intervals[0].first * bytesPerSecond)
        var segmentLength = (time.intervals[0].second - time.intervals[0].first) * audioFormat.frameRate.roundToLong()

        var segment = AudioInputStream(audioStream, audioFormat, segmentLength)

        if (time.intervals.size > 1) {
            for (i in 1 until time.intervals.size) {
                val interval = time.intervals[i]

                bufferedStream = BufferedInputStream(inputStream.inputStream())
                audioStream = AudioSystem.getAudioInputStream(bufferedStream)

                audioStream.skip(interval.first * bytesPerSecond)
                segmentLength = (interval.second - interval.first) * audioFormat.frameRate.roundToLong()

                val addSegment = AudioInputStream(audioStream, audioFormat, segmentLength)
                segment = AudioInputStream(
                    SequenceInputStream(segment, addSegment),
                    segment.format,
                    segment.frameLength + addSegment.frameLength
                )
            }
        }
        return segment
    }
}