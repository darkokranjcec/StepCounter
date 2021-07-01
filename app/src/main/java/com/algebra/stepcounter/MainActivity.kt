package com.algebra.stepcounter

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.algebra.stepcounter.databinding.ActivityMainBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.FitnessStatusCodes
import com.google.android.gms.fitness.data.DataType
import java.util.*

private const val GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 100

private const val DAYS_IN_WEEK: Int = 7
private const val DAYS_IN_MONTH: Int = 30

private const val LOG_TAG = "MainActivity"

class MainActivity : AppCompatActivity(),
    GoogleApiClient.ConnectionCallbacks,
    GoogleApiClient.OnConnectionFailedListener,
    StepsDataProviderListener {

    private lateinit var binding: ActivityMainBinding

    private val chartValues = mutableListOf<BarEntry>()

    private lateinit var client: GoogleApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupChart()
        updateChartData(chartValues)

        connectGoogleApiClient()

        connectToGoogleFit()
        setListeners()
    }

    override fun onStart() {
        super.onStart()
        client.connect()
    }

    override fun onStop() {
        super.onStop()
        client.disconnect()
    }

    private fun connectGoogleApiClient() {
        client = GoogleApiClient.Builder(this)
            .addConnectionCallbacks(this)
            .addOnConnectionFailedListener(this)
            .addApi(Fitness.RECORDING_API)
            .addApi(Fitness.HISTORY_API)
            .build()
    }

    private fun connectToGoogleFit() {
        val fitnessOptions = FitnessOptions.builder()
            .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.AGGREGATE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
            .build()

        if (GoogleSignIn.hasPermissions(GoogleSignIn.getLastSignedInAccount(this), fitnessOptions).not()){
            GoogleSignIn.requestPermissions(
                this,
                GOOGLE_FIT_PERMISSIONS_REQUEST_CODE,
                GoogleSignIn.getLastSignedInAccount(this),
                fitnessOptions
            )
        } else {
            accessGoogleFit()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == GOOGLE_FIT_PERMISSIONS_REQUEST_CODE) {
                accessGoogleFit()
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun accessGoogleFit() {
        if (binding.inputLayout.rbToday.isChecked) {
            StepsDataProvider(this, client).execute()
            return
        }

        val calendar = Calendar.getInstance()
        calendar.time = Date()
        val endTime = calendar.timeInMillis

        if (binding.inputLayout.rbLastWeek.isChecked) {
            calendar.add(Calendar.DAY_OF_YEAR, -(DAYS_IN_WEEK))
            val startTime = calendar.timeInMillis
            StepsResolver(this, this).execute(startTime, endTime)
            return
        }

        if (binding.inputLayout.rbLastMonth.isChecked) {
            calendar.add(Calendar.DAY_OF_YEAR, -(DAYS_IN_MONTH))
            val startTime = calendar.timeInMillis
            StepsResolver(this, this).execute(startTime, endTime)
            return
        }

        Toast.makeText(this, "Some problem occurred", Toast.LENGTH_LONG).show()
    }

    override fun dataFetched(result: Long) {
        updateValues(result)
    }

    private fun updateValues(value: Long) {
        val chartValue = resolveChartValues(value)
        chartValues[1] = BarEntry(1f, chartValue.toFloat())
        updateChartData(chartValues)
    }

    private fun resolveChartValues(value: Long): Long {
        if (binding.inputLayout.rbLastWeek.isChecked) {
            return value / DAYS_IN_WEEK
        }

        if (binding.inputLayout.rbLastMonth.isChecked) {
            return value / DAYS_IN_MONTH
        }

        return value
    }

    private fun updateChartData(chartValues: MutableList<BarEntry>) {
        var dataSet: BarDataSet

        if (binding.chartStepsData.data != null && binding.chartStepsData.data.dataSetCount > 0){
            dataSet = binding.chartStepsData.data.getDataSetByIndex(0) as BarDataSet
            dataSet.values = chartValues
            binding.chartStepsData.data.notifyDataChanged()
            binding.chartStepsData.notifyDataSetChanged()
        } else {
            dataSet = BarDataSet(chartValues, "DataSet")
            dataSet.colors = ColorTemplate.VORDIPLOM_COLORS.asList()
            dataSet.values = chartValues
            dataSet.label
            dataSet.setDrawValues(false)

            val dataSets = mutableListOf<BarDataSet>()
            dataSets.add(dataSet)

            val data = BarData(dataSets as List<IBarDataSet>?)
            binding.chartStepsData.data = data
            binding.chartStepsData.setFitBars(true)
        }
        binding.chartStepsData.invalidate()
    }

    private fun setupChart() {
        with(binding.chartStepsData){
        description.isEnabled = false
        setMaxVisibleValueCount(10)
        setPinchZoom(false)

        setDrawBarShadow(true)
        setDrawGridBackground(false)
        }

        val xAxis = binding.chartStepsData.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.setDrawLabels(false)

        with(binding.chartStepsData) {
            axisLeft.setDrawGridLines(false)
            animateY(1500)
            legend.isEnabled = false
        }

        chartValues.add(BarEntry(0f, 0f))
        chartValues.add(BarEntry(1f,0f))
    }

    private fun subscribe() {
        // To create a subscription, invoke the Recording API. As soon as the subscription is
        // active, fitness data will start recording.

        Fitness.RecordingApi.subscribe(client, DataType.TYPE_STEP_COUNT_CUMULATIVE)
            .setResultCallback {status ->
                if (status.isSuccess) {
                    if (status.statusCode === FitnessStatusCodes.SUCCESS_ALREADY_SUBSCRIBED) {
                        Log.d(LOG_TAG, "Existing subscription for activity detected.")
                    } else {
                        Log.d(LOG_TAG, "Successfully subscribed!")
                    }
                } else{
                    Log.d(LOG_TAG, "There was a problem subscribing.")
                }
            }
    }

    private fun setListeners() {
        binding.inputLayout.btnCheck.setOnClickListener {
            accessGoogleFit()
        }

        binding.inputLayout.btnSaveGoal.setOnClickListener {
            if(binding.inputLayout.etInputTarget.text.count() > 0){
            chartValues[0] = BarEntry(0f, binding.inputLayout.etInputTarget.text.toString().toFloat())
            } else{
                Toast.makeText(this, "There is no input number.", Toast.LENGTH_SHORT).show()
            }

            updateChartData(chartValues)
        }
    }

    override fun onConnected(p0: Bundle?) {
        subscribe()
        connectToGoogleFit()
    }

    override fun onConnectionSuspended(p0: Int) { //noop
         }

    override fun onConnectionFailed(p0: ConnectionResult) {  //noop
         }
}