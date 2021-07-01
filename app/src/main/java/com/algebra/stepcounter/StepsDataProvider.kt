package com.algebra.stepcounter

import android.os.AsyncTask
import android.util.Log
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import java.util.concurrent.TimeUnit

const val TAG = "StepsDataProvider"

class StepsDataProvider(private val listener: StepsDataProviderListener, private val client: GoogleApiClient) : AsyncTask<Void, Void, Long>() {
    override fun doInBackground(vararg params: Void): Long {
        var total: Long = 0

        val result = Fitness.HistoryApi.readDailyTotal(client, DataType.TYPE_STEP_COUNT_DELTA)
        val totalResult = result.await(30, TimeUnit.SECONDS)
        if (totalResult.status.isSuccess) {
            val totalSet = totalResult.total
            total = (if (totalSet!!.isEmpty)
                0
            else
                totalSet.dataPoints[0].getValue(Field.FIELD_STEPS).asInt()).toLong()
        } else {
            Log.d(TAG, "There was a problem getting the step count.")
        }

        Log.d(TAG, "Total steps: $total")

        return total
    }

    override fun onPostExecute(result: Long) {
        listener.dataFetched(result)
    }
}

interface StepsDataProviderListener {
    fun dataFetched(result: Long)
}