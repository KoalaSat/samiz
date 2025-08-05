package com.koalasat.samiz.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream

class Compression {
    companion object {
        fun hexStringToByteArray(hexString: String): ByteArray {
            return hexString.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }

        fun byteArrayToHexString(byteArray: ByteArray): String {
            return byteArray.joinToString("") { String.format("%02x", it) }
        }

        fun splitInChunks(message: ByteArray): Array<ByteArray> {
            val chunkSize = 500 // define the chunk size
            var byteArray = compressByteArray(message)
            val numChunks = (byteArray.size + chunkSize - 1) / chunkSize // calculate the number of chunks
            var chunkIndex = 0
            val chunks = Array(numChunks) { ByteArray(0) }

            for (i in 0 until numChunks) {
                val start = i * chunkSize
                val end = minOf((i + 1) * chunkSize, byteArray.size)
                val chunk = byteArray.copyOfRange(start, end)

                // add chunk index to the first 2 bytes and last chunk flag to the last byte
                val chunkWithIndex = ByteArray(chunk.size + 2)
                chunkWithIndex[0] = chunkIndex.toByte() // chunk index
                chunk.copyInto(chunkWithIndex, 1)
                chunkWithIndex[chunkWithIndex.size - 1] = numChunks.toByte()

                // store the chunk in the array
                chunks[i] = chunkWithIndex

                chunkIndex++
            }

            return chunks
        }

        fun joinChunks(chunks: Array<ByteArray>): ByteArray {
            val sortedChunks = chunks.sortedBy { it[0] }
            var reassembledByteArray = ByteArray(0)
            for (chunk in sortedChunks) {
                val chunkData = chunk.copyOfRange(1, chunk.size - 1)
                reassembledByteArray = reassembledByteArray.copyOf(reassembledByteArray.size + chunkData.size)
                chunkData.copyInto(reassembledByteArray, reassembledByteArray.size - chunkData.size)
            }

            return decompressByteArray(reassembledByteArray)
        }

        private fun compressByteArray(byteArray: ByteArray): ByteArray {
            if (byteArray.isEmpty()) {
                return byteArray
            }
            val bos = ByteArrayOutputStream()
            val dos = DeflaterOutputStream(bos)
            dos.write(byteArray)
            dos.close()
            return bos.toByteArray()
        }

        private fun decompressByteArray(byteArray: ByteArray): ByteArray {
            if (byteArray.isEmpty()) {
                return byteArray
            }
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
    }
}
