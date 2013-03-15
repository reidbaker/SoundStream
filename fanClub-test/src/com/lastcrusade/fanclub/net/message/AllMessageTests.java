package com.lastcrusade.fanclub.net.message;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({ ConnectFansMessageTest.class, FindNewFansMessageTest.class,
        FoundFansMessageTest.class, LibraryMessageTest.class,
        MessengerTest.class, PauseMessageTest.class, PlayMessageTest.class,
        SkipMessageTest.class, UserListMessageTest.class })
public class AllMessageTests {

}