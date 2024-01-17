package com.simprints.feature.consent.screens.consent

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simprints.core.ExternalScope
import com.simprints.core.livedata.LiveDataEventWithContent
import com.simprints.core.livedata.send
import com.simprints.core.tools.time.TimeHelper
import com.simprints.feature.consent.ConsentResult
import com.simprints.feature.consent.ConsentType
import com.simprints.feature.consent.screens.consent.helpers.GeneralConsentTextHelper
import com.simprints.feature.consent.screens.consent.helpers.ParentalConsentTextHelper
import com.simprints.feature.exitform.ExitFormConfigurationBuilder
import com.simprints.feature.exitform.ExitFormResult
import com.simprints.feature.exitform.exitFormConfiguration
import com.simprints.feature.exitform.scannerOptions
import com.simprints.infra.config.store.ConfigRepository
import com.simprints.infra.config.store.models.GeneralConfiguration
import com.simprints.infra.config.store.models.ProjectConfiguration
import com.simprints.infra.events.EventRepository
import com.simprints.infra.events.event.domain.models.ConsentEvent
import com.simprints.infra.resources.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.Serializable
import javax.inject.Inject

@HiltViewModel
internal class ConsentViewModel @Inject constructor(
    private val timeHelper: TimeHelper,
    private val configRepository: ConfigRepository,
    private val eventRepository: EventRepository,
    private val generalConsentTextHelper: GeneralConsentTextHelper,
    private val parentalConsentTextHelper: ParentalConsentTextHelper,
    @ExternalScope private val externalScope: CoroutineScope,
) : ViewModel() {

    private val startConsentEventTime = timeHelper.now()

    val viewState: LiveData<ConsentViewState>
        get() = _viewState
    private val _viewState = MutableLiveData(ConsentViewState())

    val showExitForm: LiveData<LiveDataEventWithContent<ExitFormConfigurationBuilder>>
        get() = _showExitForm
    private val _showExitForm = MutableLiveData<LiveDataEventWithContent<ExitFormConfigurationBuilder>>()

    val returnConsentResult: LiveData<LiveDataEventWithContent<Serializable>>
        get() = _returnConsentResult
    private val _returnConsentResult = MutableLiveData<LiveDataEventWithContent<Serializable>>()

    fun loadConfiguration(consentType: ConsentType) {
        viewModelScope.launch {
            val projectConfig = configRepository.getProjectConfiguration()
            _viewState.postValue(mapConfigToViewState(projectConfig, consentType))
        }
    }

    fun acceptClicked(currentConsentTab: ConsentTab) {
        saveConsentEvent(currentConsentTab, ConsentEvent.ConsentPayload.Result.ACCEPTED)
        _returnConsentResult.send(ConsentResult(true))
    }

    fun declineClicked(currentConsentTab: ConsentTab) {
        saveConsentEvent(currentConsentTab, ConsentEvent.ConsentPayload.Result.DECLINED)
        viewModelScope.launch {
            val projectConfig = configRepository.getProjectConfiguration()
            _showExitForm.send(getExitFormFromModalities(projectConfig.general.modalities))
        }
    }

    fun handleExitFormResponse(exitResult: ExitFormResult) {
        if (exitResult.wasSubmitted) {
            deleteLocationInfoFromSession()
            _returnConsentResult.send(exitResult)
        }
    }

    private fun mapConfigToViewState(
        projectConfig: ProjectConfiguration,
        consentType: ConsentType,
    ): ConsentViewState {
        val allowParentalConsent = projectConfig.consent.allowParentalConsent

        return ConsentViewState(
            showLogo = projectConfig.consent.displaySimprintsLogo,
            consentText = generalConsentTextHelper
                .assembleText(projectConfig.consent, projectConfig.general.modalities, consentType),
            showParentalConsent = allowParentalConsent,
            parentalConsentText = parentalConsentTextHelper
                .takeIf { allowParentalConsent }
                ?.assembleText(projectConfig.consent, projectConfig.general.modalities, consentType)
                .orEmpty(),
        )
    }

    private fun saveConsentEvent(
        currentConsentTab: ConsentTab,
        result: ConsentEvent.ConsentPayload.Result
    ) = externalScope.launch {
        eventRepository.addOrUpdateEvent(ConsentEvent(
            startConsentEventTime,
            timeHelper.now(),
            currentConsentTab.asEventPayload(),
            result
        ))
    }

    private fun getExitFormFromModalities(modalities: List<GeneralConfiguration.Modality>) = when {
        modalities.size != 1 -> exitFormConfiguration {
            titleRes = R.string.exit_form_title_biometrics
            backButtonRes = R.string.exit_form_continue_fingerprints_button
        }

        modalities.first() == GeneralConfiguration.Modality.FACE -> exitFormConfiguration {
            titleRes = R.string.exit_form_title_face
            backButtonRes = R.string.exit_form_continue_face_button
        }

        else -> exitFormConfiguration {
            titleRes = R.string.exit_form_title_fingerprinting
            backButtonRes = R.string.exit_form_continue_fingerprints_button
            visibleOptions = scannerOptions()
        }
    }

    private fun deleteLocationInfoFromSession() = externalScope.launch {
        eventRepository.removeLocationDataFromCurrentSession()
    }

}
