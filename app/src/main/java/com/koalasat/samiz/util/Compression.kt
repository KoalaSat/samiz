package com.koalasat.samiz.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream

class Compression {
    companion object {
        fun compressByteArray(byteArray: ByteArray): ByteArray {
            val bos = ByteArrayOutputStream()
            val dos = DeflaterOutputStream(bos)
            dos.write(byteArray)
            dos.close()
            return bos.toByteArray()
        }

        fun decompressByteArray(byteArray: ByteArray): ByteArray {
            val bis = ByteArrayInputStream(byteArray)
            val bos = ByteArrayOutputStream()
            val dis = java.util.zip.InflaterInputStream(bis)
            val buffer = ByteArray(1024)
            var len: Int
            while (dis.read(buffer).also { len = it } != -1) {
                bos.write(buffer, 0, len)
            }
            dis.close()
            return bos.toByteArray()
        }

        fun splitInChunks(byteArray: ByteArray): Array<ByteArray> {
            val chunkSize = 512 // define the chunk size
            val numChunks = (byteArray.size + chunkSize - 1) / chunkSize // calculate the number of chunks

            var chunkIndex = 0
            val chunks = Array(numChunks) { ByteArray(0) }

            for (i in 0 until numChunks) {
                val start = i * chunkSize
                val end = minOf((i + 1) * chunkSize, byteArray.size)
                val chunk = byteArray.copyOfRange(start, end)

                // add chunk index to the first byte and last chunk flag to the last byte
                val chunkWithIndex = ByteArray(chunk.size + 2)
                chunkWithIndex[0] = chunkIndex.toByte() // chunk index
                chunk.copyInto(chunkWithIndex, 1)
                chunkWithIndex[chunkWithIndex.size - 1] =
                    if
                        (i == numChunks - 1) {
                        1.toByte()
                    } else {
                        0.toByte() // last chunk flag (0 = not last, 1 = last)
                    }

                // store the chunk in the array
                chunks[i] = chunkWithIndex

                chunkIndex++
            }

            return chunks
        }

        fun joinChunks(chunks: Array<ByteArray>): ByteArray {
            var reassembledByteArray = ByteArray(0)
            for (chunk in chunks) {
                val chunkData = chunk.copyOfRange(1, chunk.size - 1)
                reassembledByteArray = reassembledByteArray.copyOf(reassembledByteArray.size + chunkData.size)
                chunkData.copyInto(reassembledByteArray, reassembledByteArray.size - chunkData.size)
            }
            return reassembledByteArray
        }
    }
}
