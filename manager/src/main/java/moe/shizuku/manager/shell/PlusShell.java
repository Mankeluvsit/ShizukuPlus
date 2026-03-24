package moe.shizuku.manager.shell;

import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;

import java.util.List;

import moe.shizuku.manager.ShizukuSettings;
import moe.shizuku.manager.utils.ActivityLogManager;
import moe.shizuku.manager.utils.ActivityLogRecord;
import moe.shizuku.server.IActivityManagerPlus;
import moe.shizuku.server.IAICorePlus;
import moe.shizuku.server.IContinuityBridge;
import moe.shizuku.server.INetworkGovernorPlus;
import moe.shizuku.server.IOverlayManagerPlus;
import moe.shizuku.server.IShizukuService;
import moe.shizuku.server.IStorageProxy;
import moe.shizuku.server.IVirtualMachineManager;
import moe.shizuku.server.IWindowManagerPlus;
import rikka.shizuku.Shizuku;

public class PlusShell {

    private static void printHelp() {
        System.out.println("ShizukuPlus CLI Helper");
        System.out.println("Usage: plus [command] [args]");
        System.out.println("");
        System.out.println("Commands:");
        System.out.println("  status            Show binder, policy, and service status");
        System.out.println("  features          Show Plus feature availability for this app");
        System.out.println("  vm list           List all Microdroid VMs");
        System.out.println("  vm start [name]   Start a specific VM");
        System.out.println("  vm stop [name]    Stop a specific VM");
        System.out.println("  vm delete [name]  Delete a specific VM");
        System.out.println("  vm status [name]  Show VM status");
        System.out.println("  log               View the privileged activity log");
        System.out.println("  doctor            Run system diagnostics");
        System.out.println("  spoof [target]    Set device identity spoofing");
        System.out.flush();
    }

    private static IShizukuService getService(IBinder binder) {
        return IShizukuService.Stub.asInterface(binder);
    }

    private static String formatState(boolean enabled, boolean accessible) {
        if (!enabled) return "disabled";
        return accessible ? "available" : "blocked";
    }

    private static void printFeatureLine(String name, boolean enabled, boolean accessible) {
        System.out.printf("%-24s %s%n", name + ":", formatState(enabled, accessible));
    }

    private static void handleLog() {
        List<ActivityLogRecord> records = ActivityLogManager.INSTANCE.getRecords();
        if (records.isEmpty()) {
            System.out.println("Activity log is empty.");
        } else {
            System.out.println("Recent Privileged Activities:");
            for (ActivityLogRecord record : records) {
                System.out.printf("[%tT] %s: %s (%s)\n", 
                    record.getTimestamp(), record.getAppName(), record.getAction(), record.getPackageName());
            }
        }
        System.out.flush();
    }

    private static void handleStatus(String packageName, IBinder binder) throws RemoteException {
        IShizukuService service = getService(binder);
        System.out.println("ShizukuPlus status");
        System.out.println("  client package: " + packageName);
        System.out.println("  server version: " + service.getVersion());
        System.out.println("  server uid: " + service.getUid());
        System.out.println("  selinux context: " + service.getSELinuxContext());
        System.out.println("  permission granted: " + service.checkSelfPermission());
        System.out.println("  custom api enabled: " + ShizukuSettings.isCustomApiEnabled());
        System.out.println("  plus access policy: " + ShizukuSettings.getPlusAccessPolicy(packageName));
        System.out.println("  spoof target: " + ShizukuSettings.getSpoofTarget());
    }

    private static void handleFeatures(String packageName, IBinder binder) throws RemoteException {
        IShizukuService service = getService(binder);

        IVirtualMachineManager vm = service.getVirtualMachineManager();
        IStorageProxy storage = service.getStorageProxy();
        IAICorePlus ai = service.getAICorePlus();
        IWindowManagerPlus window = service.getWindowManagerPlus();
        IContinuityBridge continuity = service.getContinuityBridge();
        IOverlayManagerPlus overlay = service.getOverlayManagerPlus();
        INetworkGovernorPlus network = service.getNetworkGovernorPlus();
        IActivityManagerPlus activity = service.getActivityManagerPlus();

        System.out.println("Feature availability for " + packageName);
        System.out.println("  policy: " + ShizukuSettings.getPlusAccessPolicy(packageName));
        printFeatureLine("activity_log", ShizukuSettings.isActivityLogEnabled(), true);
        printFeatureLine("custom_api", ShizukuSettings.isCustomApiEnabled(), ShizukuSettings.isCustomApiEnabled());
        printFeatureLine("su_bridge", ShizukuSettings.isSuBridgeEnabled(), ShizukuSettings.isSuBridgeEnabled()
                && ShizukuSettings.PLUS_ACCESS_POLICY_TRUSTED.equals(ShizukuSettings.getPlusAccessPolicy(packageName)));
        printFeatureLine("avf_manager", ShizukuSettings.isAvfManagerEnabled(), vm != null);
        printFeatureLine("storage_proxy", ShizukuSettings.isStorageProxyEnabled(), storage != null);
        printFeatureLine("continuity_bridge", ShizukuSettings.isContinuityBridgeEnabled(), continuity != null);
        printFeatureLine("ai_core_plus", ShizukuSettings.isAICorePlusEnabled(), ai != null);
        printFeatureLine("window_manager_plus", ShizukuSettings.isWindowManagerPlusEnabled(), window != null);
        printFeatureLine("overlay_manager_plus", ShizukuSettings.isOverlayManagerPlusEnabled(), overlay != null);
        printFeatureLine("network_governor_plus", ShizukuSettings.isNetworkGovernorPlusEnabled(), network != null);
        printFeatureLine("activity_manager_plus", ShizukuSettings.isActivityManagerPlusEnabled(), activity != null);
        printFeatureLine("spoof_device", ShizukuSettings.isSpoofDeviceEnabled(), ShizukuSettings.isSpoofDeviceEnabled());
        printFeatureLine("vector", ShizukuSettings.isVectorEnabled(), ShizukuSettings.isVectorEnabled());
    }

    private static void handleVm(String[] args, IBinder binder) throws RemoteException {
        if (args.length < 2) {
            System.out.println("Usage: plus vm [list|start|stop|delete|status]");
            return;
        }

        IVirtualMachineManager vm = getService(binder).getVirtualMachineManager();
        if (vm == null) {
            System.out.println("Virtual machine manager is unavailable. Check feature toggles and Plus access policy.");
            return;
        }

        switch (args[1]) {
            case "list":
                List<String> names = vm.list();
                if (names == null || names.isEmpty()) {
                    System.out.println("Active VMs: none");
                } else {
                    System.out.println("Active VMs:");
                    for (String name : names) {
                        System.out.println("  " + name + " (" + vm.getStatus(name) + ")");
                    }
                }
                break;
            case "start":
                if (args.length < 3) {
                    System.out.println("Missing VM name");
                } else {
                    System.out.println(vm.start(args[2]) ? "Started VM: " + args[2] : "Failed to start VM: " + args[2]);
                }
                break;
            case "stop":
                if (args.length < 3) {
                    System.out.println("Missing VM name");
                } else {
                    System.out.println(vm.stop(args[2]) ? "Stopped VM: " + args[2] : "Failed to stop VM: " + args[2]);
                }
                break;
            case "delete":
                if (args.length < 3) {
                    System.out.println("Missing VM name");
                } else {
                    System.out.println(vm.delete(args[2]) ? "Deleted VM: " + args[2] : "Failed to delete VM: " + args[2]);
                }
                break;
            case "status":
                if (args.length < 3) {
                    System.out.println("Missing VM name");
                } else {
                    System.out.println(args[2] + ": " + vm.getStatus(args[2]));
                }
                break;
            default:
                System.out.println("Unknown VM command: " + args[1]);
        }
    }

    private static void handleSpoof(String[] args) {
        if (args.length < 2) {
            System.out.println("Current spoof target: " + ShizukuSettings.getSpoofTarget());
            System.out.println("Spoofing enabled: " + ShizukuSettings.isSpoofDeviceEnabled());
            return;
        }
        ShizukuSettings.setSpoofTarget(args[1]);
        ShizukuSettings.setSpoofDeviceEnabled(true);
        ShizukuSettings.syncAllPlusFeaturesToServer();
        System.out.println("Updated spoof target: " + args[1]);
    }

    private static void handleDoctor(String packageName, IBinder binder) throws RemoteException {
        handleStatus(packageName, binder);
        System.out.println();
        handleFeatures(packageName, binder);
    }

    public static void main(String[] args, String packageName, IBinder binder, Handler handler) {
        if (args.length == 0 || args[0].equals("help")) {
            printHelp();
            System.exit(0);
        }

        Shizuku.onBinderReceived(binder, packageName);
        
        try {
            switch (args[0]) {
                case "status":
                    handleStatus(packageName, binder);
                    break;
                case "features":
                    handleFeatures(packageName, binder);
                    break;
                case "log":
                    handleLog();
                    break;
                case "vm":
                    handleVm(args, binder);
                    break;
                case "spoof":
                    handleSpoof(args);
                    break;
                case "doctor":
                    handleDoctor(packageName, binder);
                    break;
                default:
                    System.out.println("Unknown command: " + args[0]);
                    printHelp();
            }
        } catch (Throwable tr) {
            tr.printStackTrace(System.err);
        } finally {
            System.out.flush();
            System.exit(0);
        }
    }
}
