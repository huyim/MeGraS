package org.megras.api.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.int
import com.github.doyaaaaaken.kotlincsv.dsl.context.ExcessFieldsRowBehaviour
import com.github.doyaaaaaken.kotlincsv.dsl.context.InsufficientFieldsRowBehaviour
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import org.megras.data.graph.Quad
import org.megras.data.graph.QuadValue
import org.megras.graphstore.MutableQuadSet
import java.io.File

class ImportCommand(private val quads: MutableQuadSet) : CliktCommand(name = "import", help = "imports a TSV file in triple format") {

    private val fileName: String by option("-f", "--File", help = "Path of TSV file to be read for import")
        .required()

    private val batchSize: Int by option("-b", "--batchSize").int().default(100)

    override fun run() {

        val file = File(fileName)

        if (!file.exists() || !file.canRead()) {
            System.err.println("Cannot read file '${file.absolutePath}'")
            return
        }

        val tsvReader = csvReader {
            delimiter = '\t'
            escapeChar = '\\'
            skipEmptyLine = true
            insufficientFieldsRowBehaviour = InsufficientFieldsRowBehaviour.IGNORE
            excessFieldsRowBehaviour = ExcessFieldsRowBehaviour.IGNORE
        }

        val batch = mutableSetOf<Quad>()

        var counter = 0
        tsvReader.open(file) {
            readAllAsSequence(3).forEach { line ->
                if (line.size >= 3 ) {
                    val values = line.map { QuadValue.of(it) }
                    val quad = Quad(values[0], values[1], values[2])
                    batch.add(quad)
                    ++counter
                    if (batch.size >= batchSize) {
                        quads.addAll(batch)
                        batch.clear()
                        println("processed $counter lines")
                    }
                }
            }
        }

        if (batch.isNotEmpty()) {
            quads.addAll(batch)
        }

        println("Done reading $counter lines")


    }

}