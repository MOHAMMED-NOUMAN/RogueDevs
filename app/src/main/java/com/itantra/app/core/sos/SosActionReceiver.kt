package com.itantra.app.core.sos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** The SOS notification's "I'm coming" button. */
@AndroidEntryPoint
class SosActionReceiver : BroadcastReceiver() {
    @Inject lateinit var sos: SosCenter

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_ACKNOWLEDGE) sos.acknowledge()
    }

    companion object {
        const val ACTION_ACKNOWLEDGE = "com.itantra.app.action.SOS_ACKNOWLEDGE"
    }
}
