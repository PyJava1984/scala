/* NSC -- new Scala compiler
 * Copyright 2005-2010 LAMP/EPFL
 * @author  Paul Phillips
 */

package scala.tools.nsc
package settings

import io.AbstractFile

/** A Settings abstraction boiled out of the original highly mutable Settings
 *  class with the intention of creating an ImmutableSettings which can be used
 *  interchangeably.   Except of course without the mutants.
 */

trait AbsSettings {
  type Setting <: AbsSetting      // Fix to the concrete Setting type
  type ResultOfTryToSet           // List[String] in mutable, (Settings, List[String]) in immutable
  def errorFn: String => Unit
  protected def allSettings: collection.Set[Setting]

  // settings minus internal usage settings
  def visibleSettings = allSettings filterNot (_.isInternalOnly)

  // only settings which differ from default
  def userSetSettings = visibleSettings filterNot (_.isDefault)

  // checks both name and any available abbreviations
  def lookupSetting(cmd: String): Option[Setting] = allSettings find (_ respondsTo cmd)

  // two AbsSettings objects are equal if their visible settings are equal.
  override def hashCode() = visibleSettings.hashCode
  override def equals(that: Any) = that match {
    case s: AbsSettings => this.visibleSettings == s.visibleSettings
    case _              => false
  }
  override def toString() = "Settings {\n%s}\n" format (userSetSettings map ("  " + _ + "\n") mkString)
  def toConciseString = userSetSettings.mkString("(", " ", ")")

  def checkDependencies =
    visibleSettings filterNot (_.isDefault) forall (setting => setting.dependencies forall {
      case (dep, value) =>
        (Option(dep.value) exists (_.toString == value)) || {
          errorFn("incomplete option %s (requires %s)".format(setting.name, dep.name))
          false
        }
    })

  implicit lazy val SettingOrdering: Ordering[Setting] = Ordering.ordered

  trait AbsSettingValue {
    type T <: Any
    def value: T
    def isDefault: Boolean
  }

  trait AbsSetting extends Ordered[Setting] with AbsSettingValue {
    def name: String
    def helpDescription: String
    def unparse: List[String]     // A list of Strings which can recreate this setting.

    /* For tools which need to populate lists of available choices */
    def choices : List[String] = Nil

    /** In mutable Settings, these return the same object with a var set.
     *  In immutable, of course they will return a new object, which means
     *  we can't use "this.type", at least not in a non-casty manner, which
     *  is unfortunate because we lose type information without it.
     */
    def withAbbreviation(name: String): Setting
    def withHelpSyntax(help: String): Setting

    def helpSyntax: String = name
    def abbreviations: List[String] = Nil
    def dependencies: List[(Setting, String)] = Nil
    def respondsTo(label: String) = (name == label) || (abbreviations contains label)

    /** If the setting should not appear in help output, etc. */
    def isInternalOnly = false

    /** Issue error and return */
    def errorAndValue[T](msg: String, x: T): T = { errorFn(msg) ; x }

    /** After correct Setting has been selected, tryToSet is called with the
     *  remainder of the command line.  It consumes any applicable arguments and
     *  returns the unconsumed ones.
     */
    protected[nsc] def tryToSet(args: List[String]): Option[ResultOfTryToSet]

    /** Commands which can take lists of arguments in form -Xfoo:bar,baz override
     *  this method and accept them as a list.  It returns List[String] for
     *  consistency with tryToSet, and should return its incoming arguments
     *  unmodified on failure, and Nil on success.
     */
    protected[nsc] def tryToSetColon(args: List[String]): Option[ResultOfTryToSet] =
      errorAndValue("'%s' does not accept multiple arguments" format name, None)

    /** Commands which take properties in form -Dfoo=bar or -Dfoo
     */
    protected[nsc] def tryToSetProperty(args: List[String]): Option[ResultOfTryToSet] =
      errorAndValue("'%s' does not accept property style arguments" format name, None)

    /** Attempt to set from a properties file style property value.
     */
    def tryToSetFromPropertyValue(s: String): Unit = tryToSet(s :: Nil)

    def isCategory:    Boolean = List("-X", "-Y", "-P") contains name
    def isAdvanced:    Boolean = (name startsWith "-X") && name != "-X"
    def isPrivate:     Boolean = (name startsWith "-Y") && name != "-Y"
    def isStandard:    Boolean = !isAdvanced && !isPrivate && !isCategory

    def compare(that: Setting): Int = name compare that.name

    /** Equality tries to sidestep all the drama and define it simply and
     *  in one place: two AbsSetting objects are equal if their names and
     *  values compare equal.
     */
    override def equals(that: Any) = that match {
      case x: AbsSettings#AbsSetting  => (name == x.name) && (value == x.value)
      case _                          => false
    }
    override def hashCode() = (name, value).hashCode
    override def toString() = "%s = %s".format(name, value)
  }

  trait InternalSetting extends AbsSetting {
    override def isInternalOnly = true
  }
}