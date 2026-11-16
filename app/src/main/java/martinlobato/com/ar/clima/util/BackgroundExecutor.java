package martinlobato.com.ar.clima.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Utilidad simple para correr trabajo fuera del hilo principal y postear
 * el resultado de vuelta en el hilo de UI.
 */
public final class BackgroundExecutor {

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private BackgroundExecutor() {
    }

    public interface Task<T> {
        T run();
    }

    public interface Result<T> {
        void onResult(T value);
    }

    /** Corre {@code task} en background y entrega el resultado en el hilo principal. */
    public static <T> void execute(final Task<T> task, final Result<T> callback) {
        IO.execute(() -> {
            final T value = task.run();
            if (callback != null) {
                MAIN.post(() -> callback.onResult(value));
            }
        });
    }

    /** Corre un trabajo simple en background, sin resultado. */
    public static void run(final Runnable runnable) {
        IO.execute(runnable);
    }

    /** Postea un runnable en el hilo principal. */
    public static void runOnMain(Runnable runnable) {
        MAIN.post(runnable);
    }

    /** Postea un runnable en el hilo principal con una demora. */
    public static void runOnMainDelayed(Runnable runnable, long delayMillis) {
        MAIN.postDelayed(runnable, delayMillis);
    }
}
