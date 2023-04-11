package io.github.kanglong1023.m3u8.util;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.kanglong1023.m3u8.util.FutureUtil.CombineFutureResult.*;

public final class FutureUtil {

    private FutureUtil() {
    }

    public static CompletableFuture<Void> disinterest(CompletableFuture<?> future) {
        Objects.requireNonNull(future);
        CompletableFuture<Void> f = new CompletableFuture<>();
        future.whenComplete((v, ex) -> {
            if (null != ex) {
                f.completeExceptionally(ex);
            } else {
                f.complete(null);
            }
        });
        return f;
    }

    /**
     * Returns a new CompletableFuture that is completed when all the given CompletableFutures complete.
     * Different from {@link CompletableFuture#allOf(CompletableFuture[])}, if all the given CompletableFutures
     * complete normally or exceptionally, then the returned CompletableFuture also does so, the results of the
     * given CompletableFutures are not reflected in the returned CompletableFuture, but may be obtained by inspecting
     * them individually.
     * <p>
     * If no CompletableFutures are provided, returns a CompletableFuture completed with the value {@link CombineFutureResult#NORMAL}.
     */
    public static CompletableFuture<CombineFutureResult> allOf(CompletableFuture<?>... cfs) {
        return andTree(cfs, 0, cfs.length - 1);
    }

    public static CompletableFuture<CombineFutureResult> allOfColl(Collection<? extends CompletableFuture<?>> cfs) {
        return allOf(null == cfs ? new CompletableFuture[0] : cfs.toArray(new CompletableFuture[0]));
    }

    private static CompletableFuture<CombineFutureResult> andTree(CompletableFuture<?>[] cfs, int lo, int hi) {
        CompletableFuture<CombineFutureResult> r;
        if (lo > hi) {
            r = CompletableFuture.completedFuture(NORMAL);
        } else {
            CompletableFuture<?> a, b;
            int mid = (lo + hi) >>> 1;
            if ((a = (lo == mid ? cfs[lo] :
                    andTree(cfs, lo, mid))) == null ||
                    (b = (lo == hi ? a : (hi == mid + 1) ? cfs[hi] :
                            andTree(cfs, mid + 1, hi))) == null) {
                throw new NullPointerException();
            }
            r = normalRelay(a, b);
        }
        return r;
    }

    public static CompletableFuture<CombineFutureResult> normalRelay(CompletableFuture<?> aFuture,
                                                                     CompletableFuture<?> bFuture) {
        CompletableFuture<CombineFutureResult> r = new CompletableFuture<>();
        AtomicReference<CombineFutureResult> aResult = new AtomicReference<>();
        AtomicReference<CombineFutureResult> bResult = new AtomicReference<>();
        if (null == aFuture) {
            aResult.set(null);
            if (null == bFuture) {
                bResult.set(null);
                applyResult(r, aResult, bResult);
            } else {
                bFuture.whenComplete((v, ex) -> {
                    digResult(bResult, v, ex);
                    applyResult(r, aResult, bResult);
                });
            }
        } else {
            if (null == bFuture) {
                bResult.set(null);
                aFuture.whenComplete((v, ex) -> {
                    digResult(aResult, v, ex);
                    applyResult(r, aResult, bResult);
                });
            } else {
                aFuture.whenComplete((aV, aEx) -> {

                    digResult(aResult, aV, aEx);

                    bFuture.whenComplete((v, ex) -> {

                        digResult(bResult, v, ex);

                        applyResult(r, aResult, bResult);
                    });
                });
            }
        }
        return r;
    }

    private static CompletableFuture<CombineFutureResult> applyResult(CompletableFuture<CombineFutureResult> r,
                                                                      AtomicReference<CombineFutureResult> aResult,
                                                                      AtomicReference<CombineFutureResult> bResult) {
        CombineFutureResult res;
        CombineFutureResult aar = aResult.get();
        CombineFutureResult bbr = bResult.get();
        if (null == aar) {
            if (null == bbr) {
                res = NORMAL;
            } else {
                res = bbr;
            }
        } else {
            if (null == bbr) {
                res = aar;
            } else {
                if (aar == NORMAL && bbr == NORMAL) {
                    res = NORMAL;
                } else if (aar == ALL_EXCEPTION && bbr == ALL_EXCEPTION) {
                    res = ALL_EXCEPTION;
                } else {
                    res = EXCEPTION;
                }
            }
        }
        r.complete(res);
        return r;
    }

    private static void digResult(AtomicReference<CombineFutureResult> result, Object r, Throwable thr) {
        if (null == thr) {
            result.set(NORMAL);
            if (r instanceof CombineFutureResult) {
                result.set(((CombineFutureResult) r));
            }
        } else {
//            thr.printStackTrace();
            result.set(ALL_EXCEPTION);
        }
    }

    public enum CombineFutureResult {
        NORMAL, EXCEPTION, ALL_EXCEPTION;
    }

}
