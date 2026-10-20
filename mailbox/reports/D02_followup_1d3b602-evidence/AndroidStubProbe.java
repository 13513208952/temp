/** Shows that android.jar on the HOST JVM is a stub: Android behaviour cannot be executed here. */
public final class AndroidStubProbe {
    public static void main(String[] args) {
        String result;
        try { android.os.Looper.getMainLooper(); result = "EXECUTED"; }
        catch (RuntimeException stub) { result = "STUB_RUNTIME_EXCEPTION: " + stub.getMessage(); }
        System.out.println("android.os.Looper.getMainLooper -> " + result);
    }
}
