package other;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public class TwinsLock implements Lock {

    private final Sync sync = new Sync(2);

    private class Sync extends AbstractQueuedSynchronizer {

        public Sync(int count){
            setState(count);
        }

        @Override
        protected int tryAcquireShared(int reduceCount) {
            for (;;){
                int count = getState();
                int l = count - reduceCount;
                if (l < 0 || compareAndSetState(count,l)){
                    return l;
                }
            }
        }

        @Override
        protected boolean tryReleaseShared(int reduceCount) {
            for (;;){
                int count = getState();
                int l = count + reduceCount;
                if (compareAndSetState(count,l)){
                    return true;
                }
            }
        }
    }

    @Override
    public void lock() {
            sync.acquireShared(1);
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {

    }

    @Override
    public boolean tryLock() {
        return false;
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return false;
    }

    @Override
    public void unlock() {
        sync.releaseShared(1);
    }

    @Override
    public Condition newCondition() {
        return null;
    }
}
