/*
 * Copyright 2018, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.trackmysleepquality.sleeptracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.example.android.trackmysleepquality.database.SleepDatabaseDao
import com.example.android.trackmysleepquality.database.SleepNight
import com.example.android.trackmysleepquality.formatNights
import kotlinx.coroutines.*

/**
 * ViewModel for SleepTrackerFragment.
 */
class SleepTrackerViewModel(
        val database: SleepDatabaseDao,
        application: Application) : AndroidViewModel(application) {

    private var viewModelJob = Job()

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }

    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)

    private val tonight = MutableLiveData<SleepNight>()
    private val allNights = database.getAllNights()
    val allNightsString = Transformations.map(allNights) { nights ->
        formatNights(nights, application.resources)
    }
    private val _snackBar = MutableLiveData(false)
    val snackbar: LiveData<Boolean>
        get() = _snackBar

    private val _navigateSleepQuality = MutableLiveData<SleepNight>()
    val navigateSleepQuality: LiveData<SleepNight>
        get() = _navigateSleepQuality

    val startButtonVisible = Transformations.map(tonight) {
        null == it
    }

    val stopButtonVisible = Transformations.map(tonight) {
        null != it
    }

    val clearButtonVisible = Transformations.map(allNights) {
        it?.isNotEmpty()
    }

    fun doneNavigation() {
        _navigateSleepQuality.value = null
    }

    init {
        initializeTonight()
    }

    private fun initializeTonight() {

        uiScope.launch {
            tonight.value = getTonightFromDatabase()
        }

    }

    private suspend fun getTonightFromDatabase(): SleepNight? {

        return withContext(Dispatchers.IO) {
            var night = database.getCurrentNight()
            if (night?.startTime != night?.endTime) {
                night = null
            }
            night
        }
    }

    fun onStartTracking() {
        uiScope.launch {

            val newNight = SleepNight()
            insert(newNight)
            tonight.value = getTonightFromDatabase()
        }
    }

    private suspend fun insert(sleepNight: SleepNight) {
        withContext(Dispatchers.IO) {
            database.insert(sleepNight)
        }
    }

    fun onStopTracking() {
        uiScope.launch {
            val oldNight = tonight.value ?: return@launch
            oldNight.endTime = System.currentTimeMillis()
            update(oldNight)
            _navigateSleepQuality.value = oldNight
        }

    }

    private suspend fun update(oldnight: SleepNight) {
        withContext(Dispatchers.IO) {
            database.update(oldnight)
        }
    }

    fun onClear() {
        uiScope.launch {
            clear()
            tonight.value = null
            _snackBar.value = true
        }

    }

    private suspend fun clear() {
        withContext(Dispatchers.IO) {
            database.delete()
        }

    }

    fun doneSnackbar() {
        _snackBar.value = false
    }

}

