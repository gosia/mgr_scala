import com.twitter.logging.config.BareFormatterConfig
import com.twitter.logging.config.FileHandlerConfig
import com.twitter.logging.config.LoggerConfig
import com.twitter.logging.config._

import scala.util.Properties

import com.mgr.scheduler.config.SchedulerServiceConfig

object CouchConfig {
  val couchHost = Properties.envOrElse("COUCH_HOST", "localhost")
  val couchPort = Properties.envOrElse("COUCH_PORT", "6666").toInt

  // dbs
  val recipeDb = Properties.envOrElse("COUCH_DB_RECIPES", "recipes")
  val recipeVersionDb = Properties.envOrElse("COUCH_DB_RECIPE_VERSIONS", "recipe_versions")
  val recipeTransDb = Properties.envOrElse("COUCH_DB_RECIPE_TRANS", "recipe_trans")

  val ingredientDb = Properties.envOrElse("COUCH_DB_INGR", "ingredient")
  val ingredientVersionDb = Properties.envOrElse("COUCH_DB_INGR_VERSIONS", "ingredient_versions")
  val ingredientTransDb = Properties.envOrElse("COUCH_DB_INGR_TRANS", "ingredient_trans")

  val skillDb = Properties.envOrElse("COUCH_DB_SKILL", "skill")
  val skillVersionDb = Properties.envOrElse("COUCH_DB_SKILL_VERSIONS", "skill_versions")
  val skillTransDb = Properties.envOrElse("COUCH_DB_SKILL_TRANS", "skill_trans")
}

object RedisConfig {
  val redisHost = Properties.envOrElse("REDIS_HOST", "localhost")
  val redisPort = Properties.envOrElse("REDIS_PORT", "16379").toInt
}

new SchedulerServiceConfig {

  // Ostrich http admin port. Curl this for stats, etc
  admin.httpPort =  Properties.envOrElse(
    "CAPRICA_GET_ADMIN_PORT",
    "19016"
  ).toInt

  override val thriftPort = Properties.envOrElse(
    "CAPRICA_GET_PORT",
    "19015"
  ).toInt

  override val couchHost = CouchConfig.couchHost
  override val couchPort = CouchConfig.couchPort
  override val recipeDb = CouchConfig.recipeDb
  override val recipeVersionDb = CouchConfig.recipeVersionDb
  override val recipeTransDb = CouchConfig.recipeTransDb
  override val ingredientDb = CouchConfig.ingredientDb
  override val ingredientVersionDb = CouchConfig.ingredientVersionDb
  override val ingredientTransDb = CouchConfig.ingredientTransDb
  override val skillDb = CouchConfig.skillDb
  override val skillVersionDb = CouchConfig.skillVersionDb
  override val skillTransDb = CouchConfig.skillTransDb

  override val redisHost = RedisConfig.redisHost
  override val redisPort = RedisConfig.redisPort

  loggers =
    new LoggerConfig {
      level = Level.DEBUG
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
