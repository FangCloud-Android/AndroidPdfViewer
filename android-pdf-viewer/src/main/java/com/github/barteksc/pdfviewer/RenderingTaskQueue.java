package com.github.barteksc.pdfviewer;

import android.util.Log;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Stack;

/**
 * @author leixin
 * 优化任务请求队列，在快速滑动过程重，优先保证最先的任务的页面加载请求得到执行，优先保证当前页面得到执行
 */
public class RenderingTaskQueue<T> {

    // 最多可以同时有5个页面处于请求队列重
    private static int MAX_RENDER_PAGE_SIZE = 4;

    private static int STACK_FIRST = 0;

    private HashMap<Integer, Queue<T>> renderTaskMap = new HashMap<>();

    /**
     * 存储当前请求的任务队列
     */
    private Stack<Integer> renderStack = new Stack();

    private int max_stack_size;

    public RenderingTaskQueue() {
        this(MAX_RENDER_PAGE_SIZE);
    }

    public RenderingTaskQueue(int max_stack_size) {
        this.max_stack_size = max_stack_size;
    }

    /**
     * 判断是否存在执行队列重
     * @param page
     * @return
     */
    public boolean hasInQueue(int page) {
        return renderStack.contains(page);
    }

    /**
     * 请求页面
     * @param task
     * @param page
     */
    public void pushTask (T task, int page) {
        Queue<T> queue = renderTaskMap.get(page);
        if (queue == null) {
            queue = new LinkedList<>();
            queue.add(task);
            renderTaskMap.put(page, queue);
        } else {
            queue.add(task);
        }

        // 当队列超过最大限度时, 移除队头的任务
        if (renderStack.size() >= max_stack_size) {
            int stackPage = renderStack.elementAt(STACK_FIRST);
            renderTaskMap.remove(stackPage);
            Log.d("RenderingTaskQueue", "==========================>>>>>>>>>>> pushTask remove " + stackPage);
            renderStack.remove(STACK_FIRST);
        }
        // 将新的任务放到队列头
        if (!renderStack.contains(page)) {
            Log.d("RenderingTaskQueue", "==========================>>>>>>>>>>> pushTask add " + page);
            renderStack.add(page);
        }
    }

    /**
     * 从队列重获取一个可执行的Task
     */
    public T pollTask() {
        if (renderStack.isEmpty())
            return null;

        int stackPage = renderStack.peek();
        Queue<T> queue = renderTaskMap.get(stackPage);

        if (queue == null || queue.isEmpty())
            return null;

        T task = queue.poll();

        if (queue.isEmpty()) {
            renderTaskMap.remove(stackPage);
            renderStack.pop();
        }
        return task;
    }
}
