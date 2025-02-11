package com.koalasat.samiz.bluethooth

import android.util.Log
import com.vitorpamplona.negentropy.Negentropy
import com.vitorpamplona.negentropy.ReconciliationResult
import com.vitorpamplona.negentropy.storage.StorageVector

class BluetoothReconciliation {
    var storageVectorServer: StorageVector = StorageVector().apply {
        insert(1678011277, "eb6b05c2e3b008592ac666594d78ed83e7b9ab30f825b9b08878128f7500008c")
        seal()
    }
    val ne = Negentropy(storageVectorServer, 50_000)

    fun getInitialMessage(): ByteArray? {
        return ne.initiate()
    }

    fun getReconcile(message: ByteArray): ReconciliationResult {
        return ne.reconcile(message)
    }
}