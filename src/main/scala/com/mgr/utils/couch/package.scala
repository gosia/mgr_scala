package com.mgr.utils

import net.liftweb.json

package object couch {
  implicit val formats = json.Serialization.formats(json.NoTypeHints)
}
