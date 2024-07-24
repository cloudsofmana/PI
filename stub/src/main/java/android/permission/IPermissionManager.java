package android.permission;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

public interface IPermissionManager extends IInterface {

    void grantRuntimePermission(String packageName, String permissionName, int userId) throws RemoteException;

    @RequiresApi(34) // QPR2
    void grantRuntimePermission(String packageName, String permissionName, int deviceId, int userId) throws RemoteException;

    @RequiresApi(34) // QPR3
    void grantRuntimePermission(String packageName, String permissionName, String persistentDeviceId, int userId) throws RemoteException;

    void revokeRuntimePermission(String packageName, String permissionName, int userId, String reason) throws RemoteException;

    @RequiresApi(34) // QPR2
    void revokeRuntimePermission(String packageName, String permissionName, int deviceId, int userId, String reason) throws RemoteException;

    @RequiresApi(34) // QPR3
    void revokeRuntimePermission(String packageName, String permissionName, String persistentDeviceId, int userId, String reason) throws RemoteException;

    @RequiresApi(34) // QPR2
    int checkPermission(String packageName, String permissionName, int deviceId, int userId) throws RemoteException;

    @RequiresApi(34) // QPR3
    int checkPermission(String packageName, String permissionName, String persistentDeviceId, int userId) throws RemoteException;

    abstract class Stub extends Binder implements IPermissionManager {
        public static IPermissionManager asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}