import com.twitter.logging.config._


import com.mgr.scheduler.config.SchedulerServiceConfig


new SchedulerServiceConfig {
  admin.httpPort = 9111

  loggers =
    new LoggerConfig {
      level = Level.INFO
      handlers = new FileHandlerConfig {
        filename = "scheduler.log"
        roll = Policy.Daily
      }
    } :: new LoggerConfig {
      node = "stats"
      level = Level.INFO
      useParents = false
      handlers = new FileHandlerConfig {
        filename = "stats.log"
        formatter = BareFormatterConfig
      }
    }
}


