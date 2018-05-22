package com.exponea.example.view.fragments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.exponea.example.App
import com.exponea.example.R
import com.exponea.example.models.Constants
import com.exponea.example.view.base.BaseFragment
import com.exponea.sdk.Exponea
import com.exponea.sdk.models.*
import kotlinx.android.synthetic.main.fragment_main.*


class MainFragment : BaseFragment() {
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return layoutInflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Track visited screen
        trackPage(Constants.ScreenNames.mainScreen)
        (activity as AppCompatActivity).supportActionBar?.subtitle = "fetching"

        // Init listeners
        initListeners()
    }

    private fun setProgressBarVisible(visible: Boolean) {
        if (visible) {
            progressBar.visibility = View.VISIBLE
        } else {
            progressBar.visibility = View.INVISIBLE
        }
    }

    /**
     * Initialize button listener
     */
    private fun initListeners() {

        attributesButton.setOnClickListener {
            setProgressBarVisible(true)
            fetchCustomerAttributes()
        }
        recommendationsButton.setOnClickListener {
            setProgressBarVisible(true)
            fetchRecommended()

        }
        eventsButton.setOnClickListener {
            setProgressBarVisible(true)
            fetchCustomerEvents()
        }
    }

    /**
     * Method handles recommendations loading
     */
    private fun fetchRecommended() {
        // Init customer structure
        val uuid = App.instance.userIdManager.uniqueUserID
        val customerIds = CustomerIds(cookie = uuid)

        // Init recommendation structure and specify params
        val recommendation = CustomerRecommendation(
                id = uuid,
                strategy = "winner"
        )

        // Specify callbacks and start loading
        Exponea.fetchRecommendation(
                customerIds = customerIds,
                customerRecommendation = recommendation,
                onSuccess = {onFetchSuccess(it)},
                onFailure = {onFetchFailed(it)}

        )
    }

    /**
     * Method handles loading events for customer
     */
    private fun fetchCustomerEvents() {
        // Init customer structure
        val uuid = App.instance.userIdManager.uniqueUserID
        val customerIds = CustomerIds(cookie = uuid)

        // Init customers events structure and specify params
        val events = CustomerEvents(
                customerIds = customerIds,
                eventTypes = mutableListOf("event1", "event2"),
                sortOrder = "desc"
        )
        // Specify callback and start loading
        Exponea.fetchCustomerEvents(
                customerEvents = events,
                onFailure = {onFetchFailed(it)},
                onSuccess = {
                  runOnUiThread {
                      setProgressBarVisible(false)
                      resultTextView.text = it.toString()
                  }
                }
        )

    }

    /**
     * Method handles loading customer attributes and properties
     */
    private fun fetchCustomerAttributes() {
        val uuid = App.instance.userIdManager.uniqueUserID
        val customerIds = CustomerIds(cookie = uuid)
        val attributes = CustomerAttributes(customerIds)

        // Select attributes to fetch
        attributes.apply {
            withProperty("first_name")
            withProperty("email")
        }

        // Specify callbacks and start loading
        Exponea.fetchCustomerAttributes(
                customerAttributes = attributes,
                onFailure = { onFetchFailed(it) },
                onSuccess = {onFetchSuccess(it)}
        )
    }

    /**
     * Our success callback
     */
    private fun onFetchSuccess(result: Result<List<CustomerAttributeModel>>) {
       runOnUiThread {
            setProgressBarVisible(false)
            resultTextView.text = result.toString()
        }

    }

    /**
     * Our failure callback
     */
    private fun onFetchFailed(result: Result<FetchError>) {
        runOnUiThread {
            setProgressBarVisible(false)
            resultTextView.text = "Message: ${result.results.message}" +
                    "\nJson: ${result.results.jsonBody}"
        }
    }

    /**
     * Method to update ui
     */
    private fun runOnUiThread(runnable: () -> Unit) {
        Handler(Looper.getMainLooper()).post(runnable)
    }

}