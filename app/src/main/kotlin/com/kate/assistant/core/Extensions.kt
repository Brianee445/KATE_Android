package com.kate.assistant.core.utils

import android.content.Context
import android.content.Intent
import android.widget.Toast

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

fun Context.startServiceSafe(intent: Intent) = runCatching { startService(intent) }
