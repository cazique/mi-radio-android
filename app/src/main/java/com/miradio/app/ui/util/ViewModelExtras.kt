package com.miradio.app.ui.util

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.miradio.app.RadioApp

/** Atajo para llegar desde [CreationExtras] hasta la app y su [com.miradio.app.AppContainer]. */
fun CreationExtras.radioApp(): RadioApp =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as RadioApp
