package com.algebra.stepcounter

import android.content.Context
import android.os.AsyncTask
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataSet
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit

class StepsResolver(private val context: Context, private val listener: StepsDataProviderListener): AsyncTask<Long, Void, Long>() {
    override fun doInBackground(vararg params: Long?): Long {
        var total: Long

        val fitnessOptions = FitnessOptions.builder()
            .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
            .build()

        val gsa = GoogleSignIn.getAccountForExtension(context, fitnessOptions)

        val response = Fitness.getHistoryClient(context, gsa)
            .readData(DataReadRequest.Builder()
                .read(DataType.TYPE_STEP_COUNT_DELTA)
                .setTimeRange(params[0]!!, params[1]!!, TimeUnit.MILLISECONDS)
                .build()
            )

        val readDataResult = Tasks.await(response)
        val dataSet = readDataResult.getDataSet(DataType.TYPE_STEP_COUNT_DELTA)

        total = parseDataSet(dataSet)

        return total
    }

    private fun parseDataSet(dataSet: DataSet?): Long {
        var total: Long = 0
        dataSet?.let {
            if (it.isEmpty) {
                return total
            }
        }?: return total

        for (dataPoint in dataSet.dataPoints) {
            val step = dataPoint.getValue(Field.FIELD_STEPS).asInt()
            total += step
        }

        return total
    }

    override fun onPostExecute(result: Long) {
        listener.dataFetched(result)

    }
}