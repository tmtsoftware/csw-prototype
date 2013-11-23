package controllers

import play.api.mvc._

// Main controller: redirects to the Assembly1 page
object Application extends Controller {

  def index = Action {
    Redirect(routes.Assembly1.edit)
  }

}