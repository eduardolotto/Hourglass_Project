package unipd.anno3.esp.hourglassproject


import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker


class MainActivity : AppCompatActivity(), SensorEventListener {

    private var millisLeft : Long = INITIAL_MILLIS
    private var millisPassed : Long = 0
    private var timerRunning : Boolean = false
    private var initialStarted : Boolean = false
    private var typeParam : Int = 0

    // Declaration of the ImageViews of the sand level in the hourglass
    private lateinit var upBulb : ImageView
    private lateinit var bottomBulb : ImageView
    // Declaration of the timer
    private lateinit var timer : CountDownTimer
    // Declaration of the SensorManager and a SensorEvent to manage reading the accelerometer
    private lateinit var sm: SensorManager
    private lateinit var lastEvent: SensorEvent
    // Declaration of the AlarmManager and a PendingIntent to manage launching the notification
    private lateinit var am : AlarmManager
    private lateinit var pendingIntent : PendingIntent


    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "onCreate() called")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Get reference to SensorManager
        sm = getSystemService(SENSOR_SERVICE) as SensorManager

        // Creation of the notification channel
        createNotificationChannel()

        // Get buttons and the level of the sand in both bulbs
        val countDownStart : Button = findViewById(R.id.countdown_start)
        val countDownReset : Button = findViewById(R.id.countdown_reset)
        upBulb = findViewById(R.id.up_sand)
        bottomBulb = findViewById(R.id.bottom_sand)

        // Get persistent data (millisLeft and millisPassed) stored as SharedPreferences
        val preferences = getPreferences(MODE_PRIVATE)
        Log.i(TAG, "Got persistent data")

        // Set the values according to the persistent data
        millisLeft = preferences.getLong(KEY_MILLIS_LEFT, INITIAL_MILLIS)
        millisPassed = preferences.getLong(KEY_MILLIS_PASSED, 0)

        /* Get the orientation of the activity to know if modify
         * either width or length of sand in upBulb and bottomBulb,
         * and update them according to this
         */
        if(resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
            typeParam= 0
        if(resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
            typeParam = 1
        updateSandLevel(upBulb, millisLeft, typeParam)
        updateSandLevel(bottomBulb, millisPassed, typeParam)

        // If the upper bulb is there is no need to reset the sand level
        if(millisLeft == INITIAL_MILLIS) countDownReset.isEnabled = false

        // Get the instance state
        if (savedInstanceState != null) {
            Log.i(TAG, "Getting instance state")
            initialStarted = savedInstanceState.getBoolean("iS")
            timerRunning = savedInstanceState.getBoolean("tR")

            if(initialStarted) {
                countDownStart.isEnabled = false
                countDownReset.isEnabled = false
            }
            if( (!initialStarted) && (millisLeft == INITIAL_MILLIS) ) countDownReset.isEnabled = false
            if( (!initialStarted) && (millisLeft != INITIAL_MILLIS) ) countDownReset.isEnabled = true
            if(timerRunning) startTimer()
        }

        // Set the action to be performed when the Start button is pressed
        countDownStart.setOnClickListener {
            if(lastEvent.values[1].toDouble() > 8.50) startTimer()
            if(lastEvent.values[1].toDouble() < -8.50) startReverseTimer()
            countDownStart.isEnabled = false
            countDownReset.isEnabled = false
            initialStarted = true
        }
        // Set the action to be performed when the Reset button is pressed
        countDownReset.setOnClickListener {
            countDownStart.isEnabled = true
            countDownReset.isEnabled = false
            initialStarted = false
            millisLeft = INITIAL_MILLIS
            millisPassed = 0
            updateSandLevel(upBulb, millisLeft, typeParam)
            updateSandLevel(bottomBulb, millisPassed, typeParam)
        }
    }

    override fun onResume() {
        Log.i(TAG, "onResume() called")
        super.onResume()

        // Get the accelerometer sensor and register this class as a listener for it
        val accel: Sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI)

        // Ask the user the missing permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PermissionChecker.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE)
        }
    }

    override fun onPause() {
        Log.i(TAG, "onPause() called")
        super.onPause()

        // Store the persistent state
        val preferences = getPreferences(MODE_PRIVATE)
        val editor = preferences.edit()
        editor.putLong(KEY_MILLIS_LEFT, millisLeft)
        editor.putLong(KEY_MILLIS_PASSED, millisPassed)
        // Commit to storage synchronously
        editor.apply()
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy() called")
        super.onDestroy()

        // Unregister this class as a listener for the accelerometer
        sm.unregisterListener(this)
        timer = object: CountDownTimer(INITIAL_MILLIS, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {}
        }
        timer.cancel()
        cancelAlarm()
    }

    // Save the instance state
    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        Log.i(TAG, "Saving instance state")
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putBoolean("iS", initialStarted)
        savedInstanceState.putBoolean("tR", timerRunning)
    }

    override fun onSensorChanged(event: SensorEvent) {
        // The synchronized keyword is used to ensure mutually exclusive access to the sensor
        synchronized(this) {
            if(event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                lastEvent = event

                if( (initialStarted) && (!timerRunning) && (event.values[1].toDouble() > 8.50) )
                    startTimer()

                if( (timerRunning) &&
                    (event.values[0].toDouble() > 8.50 || event.values[0].toDouble() < -8.50) ) {
                    timer.cancel()
                    cancelAlarm()
                    timerRunning = false
                }

                if( (initialStarted) && (!timerRunning) && (event.values[1].toDouble() < -8.50) )
                    startReverseTimer()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        Log.d(TAG, "onAccuracyChanged: $sensor, accuracy: $accuracy")
    }

    private fun startTimer() {
        Log.i(TAG, "Timer started")
        timer = object: CountDownTimer(millisLeft, 5000) {
            override fun onTick(millisUntilFinished: Long) {
                millisPassed += 5000
                millisLeft -= 5000
                updateSandLevel(upBulb, millisLeft, typeParam)
                updateSandLevel(bottomBulb, millisPassed, typeParam)
            }
            override fun onFinish() { timerRunning = false }
        }.start()

        setAlarm(millisLeft)
        timerRunning = true
    }

    private fun startReverseTimer() {
        Log.i(TAG, "Reverse timer started")
        timer = object: CountDownTimer(millisPassed, 5000) {
            override fun onTick(millisUntilFinished: Long) {
                millisPassed -= 5000
                millisLeft += 5000
                updateSandLevel(upBulb, millisLeft, typeParam)
                updateSandLevel(bottomBulb, millisPassed, typeParam)
            }
            override fun onFinish() { timerRunning = false }
        }.start()
        setAlarm(millisPassed)
        timerRunning = true
    }

    // Set the alarm to wake up the device when the timer ended
    private fun setAlarm(millisLeft: Long) {
        am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, HourglassReceiver::class.java)
        pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val realtimeEndingMillis = System.currentTimeMillis() + millisLeft
        am.setExact(AlarmManager.RTC_WAKEUP, realtimeEndingMillis, pendingIntent)
    }

    private fun cancelAlarm() {
        am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, HourglassReceiver::class.java)
        pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        am.cancel(pendingIntent)
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel only on API level 26+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = getString(R.string.channel_name)
            val description = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            channel.description = description
            // Register the channel with the system
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        val p = grantResults[0] == PermissionChecker.PERMISSION_GRANTED
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.i(TAG, "Notification runtime permission granted: $p")
    }

    private fun updateSandLevel(imv: ImageView, millis: Long, param: Int) {
        if (param == 0) {
            imv.layoutParams.height = (TOP_SAND_LEVEL_PX * (millis.toFloat() / INITIAL_MILLIS)).toInt()
            imv.requestLayout()
        }
        if (param == 1) {
            imv.layoutParams.width = (TOP_SAND_LEVEL_PX * (millis.toFloat() / INITIAL_MILLIS)).toInt()
            imv.requestLayout()
        }
    }

    companion object {
        private val TAG = MainActivity::class.simpleName
        private const val INITIAL_MILLIS :  Long = 600000
        private const val TOP_SAND_LEVEL_PX : Int = 459
        private const val KEY_MILLIS_LEFT = "millisLeftValue"
        private const val KEY_MILLIS_PASSED = "millisPassedValue"
        private const val CHANNEL_ID = "hourglassChannel"
        private const val REQUEST_CODE = 12345
    }
}