package com.venkoi.restaurantops.core.domain.usecase

import com.venkoi.restaurantops.core.common.ids.RestaurantId
import com.venkoi.restaurantops.core.domain.model.auth.AuthSessionState
import com.venkoi.restaurantops.core.domain.model.auth.AuthUser
import com.venkoi.restaurantops.core.domain.model.startup.SaaSStartupState
import com.venkoi.restaurantops.core.domain.model.tenant.RestaurantAccess
import com.venkoi.restaurantops.core.domain.repository.AuthRepository
import com.venkoi.restaurantops.core.domain.repository.DeviceInstallationRepository
import com.venkoi.restaurantops.core.domain.repository.DeviceInstallationRevokedException
import com.venkoi.restaurantops.core.domain.repository.LocalSetupRepository
import com.venkoi.restaurantops.core.domain.repository.RestaurantRepository
import com.venkoi.restaurantops.core.domain.repository.TenantRepository
import com.venkoi.restaurantops.core.model.restaurant.Restaurant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

@OptIn(ExperimentalCoroutinesApi::class)
class ResolveSaaSStartupStateUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val tenantRepository: TenantRepository,
    private val deviceInstallationRepository: DeviceInstallationRepository,
    private val localSetupRepository: LocalSetupRepository,
    private val restaurantRepository: RestaurantRepository
) {

    operator fun invoke(): Flow<SaaSStartupState> = combine(
        authRepository.sessionState,
        localSetupRepository.observeIsSetupComplete(),
        restaurantRepository.observeRestaurant()
    ) { authState, isLocalSetupComplete, localRestaurant ->
        ResolutionInput(authState, isLocalSetupComplete, localRestaurant)
    }.flatMapLatest { input ->
        flow { emit(resolve(input)) }
    }.distinctUntilChanged()
        .catch { emit(SaaSStartupState.Error) }

    private suspend fun resolve(input: ResolutionInput): SaaSStartupState = when (val auth = input.authState) {
        AuthSessionState.Initializing -> SaaSStartupState.Loading
        AuthSessionState.SignedOut -> SaaSStartupState.RequiresAuthentication
        AuthSessionState.RefreshFailed -> if (input.hasCompletedLocalRestaurant) {
            SaaSStartupState.ReadyOffline
        } else {
            SaaSStartupState.NetworkRequired
        }
        is AuthSessionState.SignedIn -> resolveSignedIn(auth.user, input)
    }

    private suspend fun resolveSignedIn(
        user: AuthUser,
        input: ResolutionInput
    ): SaaSStartupState {
        val accesses = tenantRepository.getAccessibleRestaurants().getOrElse {
            return if (input.hasCompletedLocalRestaurant) {
                SaaSStartupState.ReadyOffline
            } else {
                SaaSStartupState.NetworkRequired
            }
        }

        if (accesses.isEmpty()) {
            return if (input.hasCompletedLocalRestaurant) {
                SaaSStartupState.TenantAccessMismatch
            } else {
                SaaSStartupState.RequiresTenantSetup(user)
            }
        }

        if (!input.hasCompletedLocalRestaurant) {
            return if (accesses.size == 1) {
                SaaSStartupState.RequiresLocalSetup(user, accesses.single())
            } else {
                SaaSStartupState.MultipleRestaurantsUnsupported
            }
        }

        val localRestaurant = requireNotNull(input.localRestaurant)
        val matches = accesses.filter { it.restaurant.id == localRestaurant.id.value }
        if (matches.size != 1) return SaaSStartupState.TenantAccessMismatch

        return ensureDeviceOrAllowOffline(user, matches.single(), localRestaurant.id)
    }

    private suspend fun ensureDeviceOrAllowOffline(
        user: AuthUser,
        restaurantAccess: RestaurantAccess,
        restaurantId: RestaurantId
    ): SaaSStartupState {
        val result = deviceInstallationRepository.ensureCurrentDeviceRegistered(restaurantId)
        return when {
            result.isSuccess -> SaaSStartupState.ReadyOnline(user, restaurantAccess)
            result.exceptionOrNull() is DeviceInstallationRevokedException ->
                SaaSStartupState.DeviceRevoked
            else -> SaaSStartupState.ReadyOffline
        }
    }

    private data class ResolutionInput(
        val authState: AuthSessionState,
        val isLocalSetupComplete: Boolean,
        val localRestaurant: Restaurant?
    ) {
        val hasCompletedLocalRestaurant: Boolean
            get() = isLocalSetupComplete && localRestaurant != null
    }
}
