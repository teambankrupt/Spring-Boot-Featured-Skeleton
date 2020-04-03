package com.example.webservice.domains.home.controllers.api

import com.example.webservice.commons.Constants
import com.example.webservice.config.security.SecurityContext
import com.example.webservice.config.security.TokenService
import com.example.webservice.domains.users.models.UserAuth
import com.example.webservice.domains.users.models.dtos.UserRequest
import com.example.webservice.domains.users.models.mappers.UserMapper
import com.example.webservice.domains.users.services.UserService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.common.OAuth2AccessToken
import org.springframework.social.connect.Connection
import org.springframework.social.connect.ConnectionFactoryLocator
import org.springframework.social.connect.UsersConnectionRepository
import org.springframework.social.connect.web.ProviderSignInUtils
import org.springframework.web.bind.annotation.*
import org.springframework.web.context.request.WebRequest
import java.util.*

@RestController
@RequestMapping("/api/v1")
@Api(tags = [Constants.Swagger.BASIC_APIS], description = Constants.Swagger.BASIC_API_DETAILS)
class ApiHomeController @Autowired constructor(
        private val userService: UserService,
        private val tokenService: TokenService,
        private val userMapper: UserMapper,
        private val connectionFactoryLocator: ConnectionFactoryLocator,
        private val connectionRepository: UsersConnectionRepository
) {
    @Value("\${baseUrl}")
    val baseUrl: String? = null

    @Value("\${token.validity}")
    lateinit var tokenValidity: String

    @PostMapping("/register/verify")
    @ApiOperation(value = Constants.Swagger.VERIFY_PHONE)
    fun verifyIdentity(@RequestParam("identity") phoneOrEmail: String): ResponseEntity<String> {

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = System.currentTimeMillis() + Integer.parseInt(this.tokenValidity)
        val sent = this.userService.requireAccountValidationByOTP(phoneOrEmail, calendar.time)

        return if (!sent) ResponseEntity.status(HttpStatus.EXPECTATION_FAILED).build()
        else ResponseEntity.ok("Token Validity: " + this.tokenValidity + " ms")
    }

    @PostMapping("/register")
    @ApiOperation(value = Constants.Swagger.REGISTER)
    fun register(@RequestParam("token") token: String,
                 @RequestBody userDto: UserRequest): ResponseEntity<OAuth2AccessToken> {

        val user = this.userService.register(token, this.userMapper.map(userDto, null))

        SecurityContext.updateAuthentication(UserAuth(user))
        return ResponseEntity.ok(tokenService.createAccessToken(user))
    }

    @PostMapping("/register/social")
    @ApiOperation(value = Constants.Swagger.REGISTER)
    fun socialRegister(@RequestParam("request") request: WebRequest,
                       @RequestBody userDto: UserRequest): ResponseEntity<OAuth2AccessToken> {
        val providerSignInUtils = ProviderSignInUtils(connectionFactoryLocator, connectionRepository)
        val connection: Connection<*> = providerSignInUtils.getConnectionFromSession(request)

        val user = this.userService.createSocialLoginUser(connection)

        SecurityContext.updateAuthentication(UserAuth(user))
        return ResponseEntity.ok(tokenService.createAccessToken(user))
    }

    @PostMapping("/change_password")
    @ApiOperation(value = Constants.Swagger.CHANGE_PASSWORD)
    fun changePassword(@RequestParam("current_password") currentPassword: String,
                       @RequestParam("new_password") newPassword: String): ResponseEntity<HttpStatus> {
        this.userService.changePassword(SecurityContext.getCurrentUser().id, currentPassword, newPassword)
        return ResponseEntity.ok().build()
    }

    // Password reset
    @GetMapping("/reset_password")
    @ResponseStatus(HttpStatus.OK)
    @ApiOperation(value = Constants.Swagger.VERIFY_RESET_PASSWORD)
    fun requestResetPassword(@RequestParam("username") username: String) {
        this.userService.handlePasswordResetRequest(username)
    }

    @PostMapping("/reset_password")
    @ResponseStatus(HttpStatus.OK)
    @ApiOperation(value = Constants.Swagger.RESET_PASSWORD)
    fun resetPassword(@RequestParam("username") username: String,
                      @RequestParam("token") token: String,
                      @RequestParam("password") password: String) {
        this.userService.resetPassword(username, token, password)
    }

}
