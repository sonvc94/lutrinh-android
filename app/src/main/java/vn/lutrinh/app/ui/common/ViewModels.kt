package vn.lutrinh.app.ui.common

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import vn.lutrinh.app.AppContainer
import vn.lutrinh.app.LuTrinhApp

/** Builds a ViewModel with the app container and the nav-argument SavedStateHandle. */
inline fun <reified VM : ViewModel> factory(
    crossinline create: (AppContainer, SavedStateHandle) -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LuTrinhApp
        create(app.container, createSavedStateHandle())
    }
}

/** Screen state shared by list/detail screens (docs/03-screens.md "states"). */
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Ready<T>(val value: T) : Loadable<T>
    data object Missing : Loadable<Nothing>
    data class Failed(val message: String) : Loadable<Nothing>
}
