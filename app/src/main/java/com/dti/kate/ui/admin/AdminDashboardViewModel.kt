package com.dti.kate.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dti.kate.network.models.*
import com.dti.kate.repository.Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminDashboardViewModel @Inject constructor(
    private val repository: Repository,
) : ViewModel() {
    
    private val _stats = MutableStateFlow<AdminDashboardStats?>(null)
    val stats: StateFlow<AdminDashboardStats?> = _stats.asStateFlow()
    
    private val _errors = MutableStateFlow<List<AdminErrorItem>>(emptyList())
    val errors: StateFlow<List<AdminErrorItem>> = _errors.asStateFlow()
    
    private val _activity = MutableStateFlow<List<AdminActivityItem>>(emptyList())
    val activity: StateFlow<List<AdminActivityItem>> = _activity.asStateFlow()
    
    private val _users = MutableStateFlow<List<AdminUserItem>>(emptyList())
    val users: StateFlow<List<AdminUserItem>> = _users.asStateFlow()
    
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()
    
    private var currentPage = 1
    private var hasMoreUsers = true
    
    init {
        loadDashboard()
    }
    
    fun loadDashboard() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Load all data in parallel
                val statsResult = repository.getAdminStats()
                val errorsResult = repository.getAdminErrors()
                val activityResult = repository.getAdminActivity()
                val usersResult = repository.getAdminUsers()
                
                statsResult.onSuccess { _stats.value = it }
                errorsResult.onSuccess { _errors.value = it.errors }
                activityResult.onSuccess { _activity.value = it.activity }
                usersResult.onSuccess { 
                    _users.value = it.users
                    hasMoreUsers = it.pagination.page < it.pagination.pages
                }
                
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun refresh() {
        currentPage = 1
        loadDashboard()
    }
    
    fun loadErrors() {
        viewModelScope.launch {
            try {
                val result = repository.getAdminErrors()
                result.onSuccess { _errors.value = it.errors }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    fun loadMoreUsers() {
        if (!hasMoreUsers || _isLoading.value) return
        
        viewModelScope.launch {
            try {
                currentPage++
                val result = repository.getAdminUsers(page = currentPage)
                result.onSuccess { 
                    _users.value = _users.value + it.users
                    hasMoreUsers = it.pagination.page < it.pagination.pages
                }
            } catch (e: Exception) {
                currentPage--
            }
        }
    }
    
    fun selectTab(index: Int) {
        _selectedTab.value = index
        when (index) {
            1 -> loadErrors()
            2 -> loadMoreUsers()
        }
    }
    
    fun logout() {
        repository.logoutLocal()
        // Navigate to login
    }
}
