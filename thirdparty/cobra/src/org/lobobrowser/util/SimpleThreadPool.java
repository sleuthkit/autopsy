package org.lobobrowser.util;

import java.util.*;
import java.util.logging.*;

/**
 * A thread pool that allows cancelling all running tasks without
 * shutting down the thread pool.
 */
public class SimpleThreadPool {
	private static final Logger logger = Logger.getLogger(SimpleThreadPool.class.getName());
	private final LinkedList taskList = new LinkedList();
	private final Set runningSet = new HashSet();
	private final int minThreads;
	private final int maxThreads;
	private final String name;
	private final int idleAliveMillis;
	private final Object taskMonitor = new Object();
	private final ThreadGroup threadGroup;
	
	private int numThreads = 0;
	private int numIdleThreads = 0;
	private int threadNumber = 0;
	
	public SimpleThreadPool(String name, int minShrinkToThreads, int maxThreads, int idleAliveMillis) {
		this.minThreads = minShrinkToThreads;
		this.maxThreads = maxThreads;
		this.idleAliveMillis = idleAliveMillis;
		this.name = name;
		// Thread group needed so item requests
		// don't get assigned sub-thread groups.
		//TODO: Thread group needs to be thought through. It's retained in
		//memory, and we need to return the right one in the GUI thread as well.
		this.threadGroup = null; //new ThreadGroup(name);
	}
	
	public void schedule(SimpleThreadPoolTask task) {
		if(task == null) {
			throw new IllegalArgumentException("null task");
		}
		Object monitor = this.taskMonitor;
		synchronized(monitor) {
			if(this.numIdleThreads == 0) {
				this.addThreadImpl();
			}
			this.taskList.add(task);
			monitor.notify();
		}
	}
	
	public void cancel(SimpleThreadPoolTask task) {
		synchronized(this.taskMonitor) {
			this.taskList.remove(task);
		}
		task.cancel();
	}
	
	private void addThreadImpl() {
		if(this.numThreads < this.maxThreads) {
			Thread t = new Thread(this.threadGroup, new ThreadRunnable(), this.name + this.threadNumber++);
			t.setDaemon(true);
			t.start();
			this.numThreads++;
		}
	}

	/**
	 * Cancels all waiting tasks and any currently running task.
	 */
	public void cancelAll() {
		synchronized(this.taskMonitor) {
			this.taskList.clear();
			Iterator i = this.runningSet.iterator();
			while(i.hasNext()) {
				((SimpleThreadPoolTask) i.next()).cancel();
			}
		}
	}

	private class ThreadRunnable implements Runnable {
		public void run() {
			Object monitor = taskMonitor;
			LinkedList tl = taskList;
			Set rs = runningSet;
			int iam = idleAliveMillis;
			SimpleThreadPoolTask task = null;
			for(;;) {
				try {
					synchronized(monitor) {
						if(task != null) {
							rs.remove(task);
						}
						numIdleThreads++;
						try {
							long waitBase = System.currentTimeMillis();
							INNER:
							while(tl.isEmpty()) {
								long maxWait = iam - (System.currentTimeMillis() - waitBase);
								if(maxWait <= 0) {
									if(numThreads > minThreads) {
										// Should be only way to exit thread.
										numThreads--;
										return;
									}
									else {
										waitBase = System.currentTimeMillis();
										continue INNER;
									}
								}
								monitor.wait(maxWait);							
							}
						} finally {
							numIdleThreads--;
						}
						task = (SimpleThreadPoolTask) taskList.removeFirst();
						rs.add(task);
					}
					Thread currentThread = Thread.currentThread();
					String baseName = currentThread.getName();
					try {
						try {
							currentThread.setName(baseName + ":" + task.toString());
						} catch(Throwable thrown) {
							logger.log(Level.WARNING, "run(): Unable to set task name.", thrown);
						}
						try {
							task.run();
						} catch(Throwable thrown) {
							logger.log(Level.SEVERE, "run(): Error in task: " + task + ".", thrown);
						}
					} finally {
						currentThread.setName(baseName);
					}
				} catch(Throwable thrown) {
					logger.log(Level.SEVERE, "run(): Error in thread pool: " + SimpleThreadPool.this.name + ".", thrown);
				}
			}
		}
	}	
}
