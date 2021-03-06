package io.apptik.roxy;


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Predicate;
import io.reactivex.subjects.Subject;

import static io.apptik.roxy.Roxy.TePolicy.PASS;
import static io.apptik.roxy.Roxy.TePolicy.WRAP;

/**
 * Roxy implementation using RxJava 2.x {@link Subject}
 */
public class RxJava2SubjProxy implements Roxy<Observable> {

    private final Subject subj;
    private final TePolicy tePolicy;

    private final Map<Observable, Disposable> subscriptions = new ConcurrentHashMap<>();

    public RxJava2SubjProxy(Subject subj, TePolicy tePolicy) {
        this.subj = subj;
        this.tePolicy = tePolicy;
    }


    /**
     * Subscribes RSProcProxy to {@link io.reactivex.Observable}.
     * If there is no RSProcProxy with the specific tag a new one will be created
     *
     * @param observable the Observable to subscribe to
     */
    @Override
    public Removable addUpstream(final Observable observable) {
        observable.subscribe(new Observer() {
            @Override
            public void onSubscribe(Disposable d) {
                subscriptions.put(observable, d);
            }

            @Override
            public void onNext(Object value) {
                subj.onNext(value);
            }

            @Override
            public void onError(Throwable e) {
                if (tePolicy.equals(WRAP)) {
                    subj.onNext(new Event.ErrorEvent(e));
                } else if (tePolicy.equals(PASS)) {
                    subj.onError(e);
                }
            }

            @Override
            public void onComplete() {
                if (tePolicy.equals(WRAP)) {
                    subj.onNext(Event.COMPLETE);
                } else if (tePolicy.equals(PASS)) {
                    subj.onComplete();
                }
            }
        });
        return new Removable() {
            @Override
            public void remove() {
                removeUpstream(observable);
            }
        };
    }

    /**
     * Unsubscribe {@link Observable} from a RSProcProxy
     *
     * @param observable the Observable to unsubscribe from
     */
    @Override
    public void removeUpstream(Observable observable) {
        synchronized (subscriptions) {
            Disposable s = subscriptions.get(observable);
            if (s != null) {
                s.dispose();
                subscriptions.remove(observable);
            }
        }
    }

    @Override
    public Observable pub() {
        return subj.hide();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Observable<T> pub(final Class<T> filterClass) {
        return subj.filter(new Predicate() {
            @Override
            public boolean test(Object obj) throws Exception {
                return filterClass.isAssignableFrom(obj.getClass());
            }
        });
    }

    @Override
    public void emit(Object event) {
        subj.onNext(event);
    }

    @Override
    public void complete() {
        subj.onComplete();
    }

    @Override
    public TePolicy tePolicy() {
        return tePolicy;
    }

    @Override
    public void clear() {
        synchronized (subscriptions) {
            for (Disposable s : subscriptions.values()) {
                s.dispose();
            }
            subscriptions.clear();
        }
    }
}
