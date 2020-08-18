package com.sudoplatform.sudotelephony

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.apollographql.apollo.GraphQLCall
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
import com.sudoplatform.sudokeymanager.KeyManagerException
import com.sudoplatform.sudokeymanager.KeyManagerFactory
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudotelephony.type.CreatePublicKeyInput
import com.sudoplatform.sudouser.SudoUserClient
import java.nio.charset.Charset
import java.security.KeyPair
import java.util.*


interface TelephonyKeyManager {
    fun removeAllKeys()
    fun generateKeyPair(callback: (Result<com.sudoplatform.sudotelephony.fragment.PublicKey>) -> Unit)
    fun getOwner(): String?
    fun getKeyPair() : KeyPair?
    fun getKeyId(): String?
    fun getKeyRingId(): String?
    fun decryptSealedData(data: ByteArray): ByteArray
}

class DefaultTelephonyKeyManager(sudoUserClient: SudoUserClient, graphQLClient: AWSAppSyncClient, context: Context, logger: Logger) : TelephonyKeyManager {
    companion object {
        private const val KEY_MANAGER_KEY_ID_NAME = "com.sudoplatform.keyId"
        private const val KEY_MANAGER_KEYRING_ID_NAME = "com.sudoplatform.keyRingId"
        private const val RSA_ENCRYPTION_OAEP_AES_CBC = "RSAEncryptionOAEPAESCBC"
    }
    var sudoKeyManager: KeyManagerInterface
    val sudoUserClient: SudoUserClient
    val graphQlClient: AWSAppSyncClient
    val context: Context
    val logger: Logger

    init {
        this.sudoKeyManager = KeyManagerFactory(context).createAndroidKeyManager()
        this.sudoUserClient = sudoUserClient
        this.graphQlClient = graphQLClient
        this.context = context
        this.logger = logger
    }

    override fun getOwner(): String? {
        return this.sudoUserClient.getSubject()
    }

    override fun removeAllKeys() {
        this.sudoKeyManager.removeAllKeys()
    }

    private fun getIdentityId() : String? {
        val identityId = this.sudoUserClient.getUserClaim("custom:identityId")
        return identityId as? String
    }

    override fun generateKeyPair(callback: (Result<com.sudoplatform.sudotelephony.fragment.PublicKey>) -> Unit) {
        val identityId = this.getIdentityId()

        if (identityId == null) {
            val error = TelephonyCreatePublicKeyException()
            logger.error("Unable to retrieve identity id")
            callback.runOnUiThread()(Result.Error(error))
            return
        }

        val keyRingId = UUID.randomUUID().toString()
        val keyId = UUID.randomUUID().toString()

        var publicKey: String? = null
        try {
            this.sudoKeyManager.generateKeyPair(identityId)
            val pubKey = this.sudoKeyManager.getPublicKeyData(identityId) ?: throw (KeyManagerException(
                "Failed to generate key pair"
            ))
            publicKey = Base64.getEncoder().encodeToString(pubKey)
        } catch (error: KeyManagerException) {
            logger.error("Failed to generate key pair: $error")
        }

        if (publicKey == null) {
            val error = TelephonyCreatePublicKeyException()
            logger.error("Failed to create public key: $error")
            callback.runOnUiThread()(Result.Error(error))
            return
        }

        this.sudoKeyManager.addPassword(
            keyId.toByteArray(Charset.forName("UTF-8")),
            KEY_MANAGER_KEY_ID_NAME + identityId
        )
        this.sudoKeyManager.addPassword(
            keyRingId.toByteArray(Charset.forName("UTF-8")),
            KEY_MANAGER_KEYRING_ID_NAME + identityId
        )
        val publicKeyInput = CreatePublicKeyInput.builder()
            .publicKey(publicKey!!)
            .keyId(keyId)
            .keyRingId(keyRingId)
            .algorithm(RSA_ENCRYPTION_OAEP_AES_CBC)
            .build()

        val mutation = CreatePublicKeyMutation.builder().input(publicKeyInput).build()
        graphQlClient.mutate(mutation)
            .enqueue(object : GraphQLCall.Callback<CreatePublicKeyMutation.Data>() {
                override fun onResponse(response: Response<CreatePublicKeyMutation.Data>) {
                    val pubKey =
                        response.data()?.createPublicKeyForTelephony?.fragments()?.publicKey()
                    if (pubKey != null) {
                        callback.runOnUiThread()(Result.Success(pubKey))
                    } else {
                        val error = TelephonyCreatePublicKeyException()
                        logger.error("Failed to create public key: $error")
                        callback.runOnUiThread()(Result.Error(error))
                    }
                }

                override fun onFailure(e: ApolloException) {
                    val error = TelephonyCreatePublicKeyException(e)
                    logger.error("Failed to create public key: $error")
                    callback.runOnUiThread()(Result.Error(error))
                }
            })
    }

    override fun getKeyPair(): KeyPair? {
        val identityId = this.getIdentityId() ?: return null
        val privateKey = this.sudoKeyManager.getPrivateKey(identityId) ?: return null
        val publicKey = this.sudoKeyManager.getPublicKey(identityId) ?: return null

        return KeyPair(publicKey, privateKey)
    }

    override fun getKeyId(): String? {
        val identityId = this.getIdentityId() ?: return null
        return this.sudoKeyManager.getPassword(KEY_MANAGER_KEY_ID_NAME + identityId)
            .toString(Charset.forName("UTF-8"))
    }

    override fun getKeyRingId(): String? {
        val identityId = this.getIdentityId() ?: return null
        return this.sudoKeyManager.getPassword(KEY_MANAGER_KEYRING_ID_NAME + identityId)
            .toString(Charset.forName("UTF-8"))
    }

    override fun decryptSealedData(data: ByteArray): ByteArray {
        if (data.size < 1) {
            return data
        }

        try {
            val encryptedCipherKey = data.copyOfRange(0, 256)
            val cipherKey = this.sudoKeyManager.decryptWithPrivateKey(
                this.getIdentityId(),
                encryptedCipherKey,
                KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_OAEPSHA1
            )
            val symmetricKey = cipherKey.copyOfRange(0, 32)
            val iv = ByteArray(16, { 0 })
            val encryptedData = data.copyOfRange(256, data.size)

            return this.sudoKeyManager.decryptWithSymmetricKey(symmetricKey, encryptedData, iv)
        } catch (error: KeyManagerException) {
            logger.error("Failed to decrypt sealed data")
            throw(TelephonyDecryptSealedDataException(error))
        }
    }
}