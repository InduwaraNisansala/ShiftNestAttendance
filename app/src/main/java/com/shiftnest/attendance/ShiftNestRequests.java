package com.shiftnest.attendance;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.io.IOException;

/** Bounds waiting without claiming that a timed-out server write was cancelled. */
public final class ShiftNestRequests {
    private ShiftNestRequests() {}
    public static <T> Task<T> read(Task<T> task) {
        return Tasks.withTimeout(task,30,TimeUnit.SECONDS);
    }
    public static <T> Task<T> write(Task<T> task) {
        return Tasks.withTimeout(task,45,TimeUnit.SECONDS).continueWithTask(result->{
            if(result.isSuccessful())return Tasks.forResult(result.getResult());
            if(result.isCanceled())return Tasks.forCanceled();
            Exception failure=result.getException();
            if(failure instanceof TimeoutException)failure=new IOException("Server confirmation timed out. The save may still finish. Check the saved record before retrying.");
            return Tasks.forException(failure==null?new IOException("Request failed"):failure);
        });
    }
}
