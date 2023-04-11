package io.github.kanglong1023.m3u8.support.shaded.org.jctools.queues;

import static io.github.kanglong1023.m3u8.support.shaded.org.jctools.util.UnsafeRefArrayAccess.REF_ARRAY_BASE;
import static io.github.kanglong1023.m3u8.support.shaded.org.jctools.util.UnsafeRefArrayAccess.REF_ELEMENT_SHIFT;

/**
 * This is used for method substitution in the LinkedArray classes code generation.
 */
public final class LinkedArrayQueueUtil
{
    public static int length(Object[] buf)
    {
        return buf.length;
    }

    /**
     * This method assumes index is actually (index << 1) because lower bit is
     * used for resize. This is compensated for by reducing the element shift.
     * The computation is constant folded, so there's no cost.
     */
    public static long modifiedCalcCircularRefElementOffset(long index, long mask)
    {
        return REF_ARRAY_BASE + ((index & mask) << (REF_ELEMENT_SHIFT - 1));
    }

    public static long nextArrayOffset(Object[] curr)
    {
        return REF_ARRAY_BASE + ((long) (length(curr) - 1) << REF_ELEMENT_SHIFT);
    }
}
