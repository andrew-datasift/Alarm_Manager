
package com.datasift.operations.alarmmanager;

class ThreadTest implements Runnable {

	Thread runner;
	public ThreadTest() {
	}
	public ThreadTest(String threadName) {
		runner = new Thread(this, threadName); // (1) Create a new thread.
		System.out.println(runner.getName());
		runner.start(); // (2) Start the thread.
	}
        
        @Override
	public void run(){
		//Display info about this particular thread
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                    
                }
                
		System.out.println(Thread.currentThread());
	}
}
