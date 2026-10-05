package com.jonkryl.binarygarden.core

/** Rendering cannot clear a write failure; only another actual commit result can. */
class SaveFeedback {
    private var failed = false

    fun commit(write: () -> Boolean): Boolean {
        val succeeded = write()
        failed = !succeeded
        return succeeded
    }

    fun message(ordinary: String, saveError: String): String = if (failed) saveError else ordinary
}
