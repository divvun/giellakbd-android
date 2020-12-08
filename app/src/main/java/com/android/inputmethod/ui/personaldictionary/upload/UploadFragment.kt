package com.android.inputmethod.ui.personaldictionary.upload

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.databinding.FragmentPersonalUploadBinding
import com.android.inputmethod.usecases.UploadUseCase
import com.jakewharton.rxbinding3.view.clicks
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import no.divvun.dictionary.personal.PersonalDictionaryDatabase
import no.divvun.service.DivvunDictionaryUploadService
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory

class UploadFragment : Fragment(), UploadView {
    private lateinit var disposable: Disposable

    private lateinit var database: PersonalDictionaryDatabase
    private lateinit var uploadUseCase: UploadUseCase
    private lateinit var presenter: UploadPresenter

    private lateinit var binding: FragmentPersonalUploadBinding

    private val retrofit = Retrofit.Builder()
            .baseUrl(DivvunDictionaryUploadService.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .build()

    private val divvunDictionaryUploadService = retrofit.create(DivvunDictionaryUploadService::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = PersonalDictionaryDatabase.getInstance(context!!)

        // TODO add languageId
        uploadUseCase = UploadUseCase(0, database, divvunDictionaryUploadService)
        presenter = UploadPresenter(this, uploadUseCase)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            FragmentPersonalUploadBinding.inflate(inflater, container, false).also {
                binding = it
            }.root

    override fun onResume() {
        super.onResume()
        disposable = presenter.start().observeOn(AndroidSchedulers.mainThread()).subscribe(::render)
    }

    override fun onPause() {
        super.onPause()
        disposable.dispose()
    }

    override fun render(viewState: UploadViewState) {
        binding.bUploadUpload.isEnabled = viewState.uploadEnabled
        binding.tvUploadError.text = viewState.errorMessage
        binding.tvUploadError.isGone = viewState.errorMessage == null

        binding.pbUploadLoading.isVisible = viewState.loading
    }

    override fun events(): Observable<UploadEvent> {
        return binding.bUploadUpload.clicks().map { UploadEvent.OnUploadPressed }
    }

    override fun navigateToSuccess() {
        // Ugly hack to not move whole presenter to UI thread.
        activity?.runOnUiThread {
            Toast.makeText(context, getString(R.string.dictionary_upload_success), Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        }
    }

}
