package moe.shizuku.manager

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.ServiceManager
import com.rosan.dhizuku.IDhizuku
import moe.shizuku.manager.authorization.AuthorizationManager
import moe.shizuku.manager.utils.ShizukuStateMachine

class DhizukuProvider : ContentProvider() {

    companion object {
        private const val KEY_ERROR = "error"
        private const val KEY_RESULT = "result"
        private const val ERROR_UNAUTHORIZED = "unauthorized"
        private const val ERROR_UNAVAILABLE = "service_unavailable"
        private const val ERROR_UNSUPPORTED = "unsupported_transaction"
    }

    private fun isCallerAuthorized(callingUid: Int): Boolean {
        if (callingUid == android.os.Process.myUid()) {
            return true
        }

        val packageManager = context?.packageManager ?: return false
        val packages = packageManager.getPackagesForUid(callingUid) ?: return false
        return packages.any { packageName -> AuthorizationManager.granted(packageName, callingUid) }
    }

    private val binder = object : IDhizuku.Stub() {
        override fun getVersion(): Int = 1

        override fun getBinder(): IBinder? {
            if (!ShizukuSettings.isDhizukuModeEnabled()) return null
            if (!ShizukuStateMachine.isRunning()) return null
            val callingUid = Binder.getCallingUid()
            if (!isCallerAuthorized(callingUid)) return null

            return try {
                ServiceManager.getService(Context.DEVICE_POLICY_SERVICE)
            } catch (e: Exception) {
                null
            }
        }

        override fun isPermissionGranted(): Boolean {
            if (!ShizukuSettings.isDhizukuModeEnabled()) return false
            return isCallerAuthorized(Binder.getCallingUid())
        }

        override fun transact(code: Int, data: Bundle?): Bundle {
            val callingUid = Binder.getCallingUid()
            if (!ShizukuSettings.isDhizukuModeEnabled()) {
                return errorBundle(ERROR_UNAVAILABLE)
            }
            if (!ShizukuStateMachine.isRunning()) {
                return errorBundle(ERROR_UNAVAILABLE)
            }
            if (!isCallerAuthorized(callingUid)) {
                return errorBundle(ERROR_UNAUTHORIZED)
            }

            // Fail closed until we implement an explicit, audited proxy layer for
            // each Dhizuku transaction shape instead of silently returning success.
            return errorBundle(ERROR_UNSUPPORTED).apply {
                putInt(KEY_RESULT, code)
            }
        }

        private fun errorBundle(error: String): Bundle {
            return Bundle().apply {
                putString(KEY_ERROR, error)
            }
        }
    }

    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if ("getBinder" == method) {
            if (!ShizukuSettings.isDhizukuModeEnabled()) return null
            if (!ShizukuStateMachine.isRunning()) return null
            if (!isCallerAuthorized(Binder.getCallingUid())) return null

            val bundle = Bundle()
            bundle.putBinder("binder", binder.asBinder())
            return bundle
        }
        return null
    }
}
