/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.inputmethod.keyboard

import com.android.inputmethod.latin.common.Constants.ImeOption.FORCE_ASCII
import com.android.inputmethod.latin.common.Constants.ImeOption.NO_SETTINGS_KEY

import android.content.Context
import android.content.res.Resources
import android.content.res.TypedArray
import android.content.res.XmlResourceParser
import android.text.InputType
import android.util.Log
import android.util.SparseArray
import android.util.Xml
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodSubtype

import com.android.inputmethod.compat.EditorInfoCompatUtils
import com.android.inputmethod.compat.InputMethodSubtypeCompatUtils
import com.android.inputmethod.compat.UserManagerCompatUtils
import com.android.inputmethod.keyboard.internal.KeyboardBuilder
import com.android.inputmethod.keyboard.internal.KeyboardParams
import com.android.inputmethod.keyboard.internal.UniqueKeysCache
import com.android.inputmethod.latin.InputAttributes
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.RichInputMethodSubtype
import com.android.inputmethod.latin.define.DebugFlags
import com.android.inputmethod.latin.utils.InputTypeUtils
import com.android.inputmethod.latin.utils.ScriptUtils
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils
import com.android.inputmethod.latin.utils.XmlParseUtils

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

import java.io.IOException
import java.lang.ref.SoftReference
import java.util.HashMap

/**
 * This class represents a set of keyboard layouts. Each of them represents a different keyboard
 * specific to a keyboard state, such as alphabet, symbols, and so on.  Layouts in the same
 * [KeyboardLayoutSet] are related to each other.
 * A [KeyboardLayoutSet] needs to be created for each
 * [android.view.inputmethod.EditorInfo].
 */
class KeyboardLayoutSet internal constructor(private val mContext: Context, private val mParams: Params) {

    val scriptId: Int
        get() = mParams.mScriptId

    class KeyboardLayoutSetException(cause: Throwable, val mKeyboardId: KeyboardId) : RuntimeException(cause)

    internal class ElementParams {
        internal var mKeyboardXmlId: Int = 0
        internal var mProximityCharsCorrectionEnabled: Boolean = false
        internal var mSupportsSplitLayout: Boolean = false
        internal var mAllowRedundantMoreKeys: Boolean = false
    }

    class Params {
        internal var mKeyboardLayoutSetName: String? = null
        internal var mMode: Int = 0
        internal var mDisableTouchPositionCorrectionDataForTest: Boolean = false
        // TODO: Use {@link InputAttributes} instead of these variables.
        internal var mEditorInfo: EditorInfo? = null
        internal var mIsPasswordField: Boolean = false
        internal var mVoiceInputKeyEnabled: Boolean = false
        internal var mNoSettingsKey: Boolean = false
        internal var mLanguageSwitchKeyEnabled: Boolean = false
        internal var mSubtype: RichInputMethodSubtype? = null
        internal var mIsSpellChecker: Boolean = false
        internal var mKeyboardWidth: Int = 0
        internal var mKeyboardHeight: Int = 0
        internal var mScriptId = ScriptUtils.SCRIPT_LATIN
        // Indicates if the user has enabled the split-layout preference
        // and the required ProductionFlags are enabled.
        internal var mIsSplitLayoutEnabledByUser: Boolean = false
        // Indicates if split layout is actually enabled, taking into account
        // whether the user has enabled it, and the keyboard layout supports it.
        internal var mIsSplitLayoutEnabled: Boolean = false
        // Sparse array of KeyboardLayoutSet element parameters indexed by element's id.
        internal val mKeyboardLayoutSetElementIdToParamsMap = SparseArray<ElementParams>()
    }

    fun getKeyboard(baseKeyboardLayoutSetElementId: Int): Keyboard {
        val keyboardLayoutSetElementId: Int
        when (mParams.mMode) {
            KeyboardId.MODE_PHONE -> if (baseKeyboardLayoutSetElementId == KeyboardId.ELEMENT_SYMBOLS) {
                keyboardLayoutSetElementId = KeyboardId.ELEMENT_PHONE_SYMBOLS
            } else {
                keyboardLayoutSetElementId = KeyboardId.ELEMENT_PHONE
            }
            KeyboardId.MODE_NUMBER, KeyboardId.MODE_DATE, KeyboardId.MODE_TIME, KeyboardId.MODE_DATETIME -> keyboardLayoutSetElementId = KeyboardId.ELEMENT_NUMBER
            else -> keyboardLayoutSetElementId = baseKeyboardLayoutSetElementId
        }

        var elementParams: ElementParams? = mParams.mKeyboardLayoutSetElementIdToParamsMap.get(
                keyboardLayoutSetElementId)
        if (elementParams == null) {
            elementParams = mParams.mKeyboardLayoutSetElementIdToParamsMap.get(
                    KeyboardId.ELEMENT_ALPHABET)
        }
        // Note: The keyboard for each shift state, and mode are represented as an elementName
        // attribute in a keyboard_layout_set XML file.  Also each keyboard layout XML resource is
        // specified as an elementKeyboard attribute in the file.
        // The KeyboardId is an internal key for a Keyboard object.

        mParams.mIsSplitLayoutEnabled = mParams.mIsSplitLayoutEnabledByUser && elementParams!!.mSupportsSplitLayout
        val id = KeyboardId(keyboardLayoutSetElementId, mParams)
        try {
            return getKeyboard(elementParams, id)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Can't create keyboard: $id", e)
            throw KeyboardLayoutSetException(e, id)
        }

    }

    private fun getKeyboard(elementParams: ElementParams?, id: KeyboardId): Keyboard {
        val ref = sKeyboardCache[id]
        val cachedKeyboard = ref?.get()
        if (cachedKeyboard != null) {
            if (DEBUG_CACHE) {
                Log.d(TAG, "keyboard cache size=" + sKeyboardCache.size + ": HIT  id=" + id)
            }
            return cachedKeyboard
        }

        val builder = KeyboardBuilder(mContext, KeyboardParams(sUniqueKeysCache))
        sUniqueKeysCache.setEnabled(id.isAlphabetKeyboard)
        builder.setAllowRedundantMoreKes(elementParams!!.mAllowRedundantMoreKeys)
        val keyboardXmlId = elementParams.mKeyboardXmlId
        builder.load(keyboardXmlId, id)
        if (mParams.mDisableTouchPositionCorrectionDataForTest) {
            builder.disableTouchPositionCorrectionDataForTest()
        }
        builder.setProximityCharsCorrectionEnabled(elementParams.mProximityCharsCorrectionEnabled)
        val keyboard = builder.build()
        sKeyboardCache[id] = SoftReference(keyboard)
        if ((id.mElementId == KeyboardId.ELEMENT_ALPHABET || id.mElementId == KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED) && !mParams.mIsSpellChecker) {
            // We only forcibly cache the primary, "ALPHABET", layouts.
            for (i in sForcibleKeyboardCache.size - 1 downTo 1) {
                sForcibleKeyboardCache[i] = sForcibleKeyboardCache[i - 1]
            }
            sForcibleKeyboardCache[0] = keyboard
            if (DEBUG_CACHE) {
                Log.d(TAG, "forcing caching of keyboard with id=$id")
            }
        }
        if (DEBUG_CACHE) {
            Log.d(TAG, "keyboard cache size=" + sKeyboardCache.size + ": "
                    + (if (ref == null) "LOAD" else "GCed") + " id=" + id)
        }
        return keyboard
    }

    class Builder(private val mContext: Context, ei: EditorInfo?) {
        private val mPackageName: String
        private val mResources: Resources

        private val mParams = Params()

        init {
            mPackageName = mContext.packageName
            mResources = mContext.resources
            val params = mParams

            val editorInfo = ei ?: EMPTY_EDITOR_INFO
            params.mMode = getKeyboardMode(editorInfo)
            // TODO: Consolidate those with {@link InputAttributes}.
            params.mEditorInfo = editorInfo
            params.mIsPasswordField = InputTypeUtils.isPasswordInputType(editorInfo.inputType)
            params.mNoSettingsKey = InputAttributes.inPrivateImeOptions(
                    mPackageName, NO_SETTINGS_KEY, editorInfo)

            // When the device is still unlocked, features like showing the IME setting app need to
            // be locked down.
            // TODO: Switch to {@code UserManagerCompat.isUserUnlocked()} in the support-v4 library
            // when it becomes publicly available.
            @UserManagerCompatUtils.LockState
            val lockState = UserManagerCompatUtils.getUserLockState(mContext)
            if (lockState == UserManagerCompatUtils.LOCK_STATE_LOCKED) {
                params.mNoSettingsKey = true
            }
        }

        fun setKeyboardGeometry(keyboardWidth: Int, keyboardHeight: Int): Builder {
            mParams.mKeyboardWidth = keyboardWidth
            mParams.mKeyboardHeight = keyboardHeight
            return this
        }

        fun setSubtype(subtype: RichInputMethodSubtype): Builder {
            val asciiCapable = InputMethodSubtypeCompatUtils.isAsciiCapable(subtype)
            // TODO: Consolidate with {@link InputAttributes}.
            val deprecatedForceAscii = InputAttributes.inPrivateImeOptions(
                    mPackageName, FORCE_ASCII, mParams.mEditorInfo)
            val forceAscii = EditorInfoCompatUtils.hasFlagForceAscii(
                    mParams.mEditorInfo!!.imeOptions) || deprecatedForceAscii
            val keyboardSubtype = if (forceAscii && !asciiCapable)
                RichInputMethodSubtype.noLanguageSubtype
            else
                subtype
            mParams.mSubtype = keyboardSubtype
            mParams.mKeyboardLayoutSetName = KEYBOARD_LAYOUT_SET_RESOURCE_PREFIX + keyboardSubtype.keyboardLayoutSetName
            return this
        }

        fun setIsSpellChecker(isSpellChecker: Boolean): Builder {
            mParams.mIsSpellChecker = isSpellChecker
            return this
        }

        fun setVoiceInputKeyEnabled(enabled: Boolean): Builder {
            mParams.mVoiceInputKeyEnabled = enabled
            return this
        }

        fun setLanguageSwitchKeyEnabled(enabled: Boolean): Builder {
            mParams.mLanguageSwitchKeyEnabled = enabled
            return this
        }

        fun disableTouchPositionCorrectionData(): Builder {
            mParams.mDisableTouchPositionCorrectionDataForTest = true
            return this
        }

        fun setSplitLayoutEnabledByUser(enabled: Boolean): Builder {
            mParams.mIsSplitLayoutEnabledByUser = enabled
            return this
        }

        fun build(): KeyboardLayoutSet {
            if (mParams.mSubtype == null)
                throw RuntimeException("KeyboardLayoutSet subtype is not specified")
            val xmlId = getXmlId(mResources, mParams.mKeyboardLayoutSetName)
            try {
                parseKeyboardLayoutSet(mResources, xmlId)
            } catch (e: IOException) {
                throw RuntimeException(e.message + " in " + mParams.mKeyboardLayoutSetName,
                        e)
            } catch (e: XmlPullParserException) {
                throw RuntimeException(e.message + " in " + mParams.mKeyboardLayoutSetName, e)
            }

            return KeyboardLayoutSet(mContext, mParams)
        }

        @Throws(XmlPullParserException::class, IOException::class)
        private fun parseKeyboardLayoutSet(res: Resources, resId: Int) {
            val parser = res.getXml(resId)
            try {
                while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                    val event = parser.next()
                    if (event == XmlPullParser.START_TAG) {
                        val tag = parser.name
                        if (TAG_KEYBOARD_SET == tag) {
                            parseKeyboardLayoutSetContent(parser)
                        } else {
                            throw XmlParseUtils.IllegalStartTag(parser, tag, TAG_KEYBOARD_SET)
                        }
                    }
                }
            } finally {
                parser.close()
            }
        }

        @Throws(XmlPullParserException::class, IOException::class)
        private fun parseKeyboardLayoutSetContent(parser: XmlPullParser) {
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                val event = parser.next()
                if (event == XmlPullParser.START_TAG) {
                    val tag = parser.name
                    if (TAG_ELEMENT == tag) {
                        parseKeyboardLayoutSetElement(parser)
                    } else if (TAG_FEATURE == tag) {
                        mParams.mScriptId = readScriptIdFromTagFeature(mResources, parser)
                    } else {
                        throw XmlParseUtils.IllegalStartTag(parser, tag, TAG_KEYBOARD_SET)
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    val tag = parser.name
                    if (TAG_KEYBOARD_SET == tag) {
                        break
                    }
                    throw XmlParseUtils.IllegalEndTag(parser, tag, TAG_KEYBOARD_SET)
                }
            }
        }

        @Throws(XmlPullParserException::class, IOException::class)
        private fun parseKeyboardLayoutSetElement(parser: XmlPullParser) {
            val a = mResources.obtainAttributes(Xml.asAttributeSet(parser),
                    R.styleable.KeyboardLayoutSet_Element)
            try {
                XmlParseUtils.checkAttributeExists(a,
                        R.styleable.KeyboardLayoutSet_Element_elementName, "elementName",
                        TAG_ELEMENT, parser)
                XmlParseUtils.checkAttributeExists(a,
                        R.styleable.KeyboardLayoutSet_Element_elementKeyboard, "elementKeyboard",
                        TAG_ELEMENT, parser)
                XmlParseUtils.checkEndTag(TAG_ELEMENT, parser)

                val elementParams = ElementParams()
                val elementName = a.getInt(
                        R.styleable.KeyboardLayoutSet_Element_elementName, 0)
                elementParams.mKeyboardXmlId = a.getResourceId(
                        R.styleable.KeyboardLayoutSet_Element_elementKeyboard, 0)
                elementParams.mProximityCharsCorrectionEnabled = a.getBoolean(
                        R.styleable.KeyboardLayoutSet_Element_enableProximityCharsCorrection,
                        false)
                elementParams.mSupportsSplitLayout = a.getBoolean(
                        R.styleable.KeyboardLayoutSet_Element_supportsSplitLayout, false)
                elementParams.mAllowRedundantMoreKeys = a.getBoolean(
                        R.styleable.KeyboardLayoutSet_Element_allowRedundantMoreKeys, true)
                mParams.mKeyboardLayoutSetElementIdToParamsMap.put(elementName, elementParams)
            } finally {
                a.recycle()
            }
        }

        companion object {

            private val EMPTY_EDITOR_INFO = EditorInfo()

            // Super redux version of reading the script ID for some subtype from Xml.
            internal fun readScriptId(resources: Resources, subtype: InputMethodSubtype): Int {
                val layoutSetName = KEYBOARD_LAYOUT_SET_RESOURCE_PREFIX + SubtypeLocaleUtils.getKeyboardLayoutSetName(subtype)
                val xmlId = getXmlId(resources, layoutSetName)
                val parser = resources.getXml(xmlId)
                try {
                    while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                        // Bovinate through the XML stupidly searching for TAG_FEATURE, and read
                        // the script Id from it.
                        parser.next()
                        val tag = parser.name
                        if (TAG_FEATURE == tag) {
                            return readScriptIdFromTagFeature(resources, parser)
                        }
                    }
                } catch (e: IOException) {
                    throw RuntimeException(e.message + " in " + layoutSetName, e)
                } catch (e: XmlPullParserException) {
                    throw RuntimeException(e.message + " in " + layoutSetName, e)
                } finally {
                    parser.close()
                }
                // If the tag is not found, then the default script is Latin.
                return ScriptUtils.SCRIPT_LATIN
            }

            @Throws(IOException::class, XmlPullParserException::class)
            private fun readScriptIdFromTagFeature(resources: Resources,
                                                   parser: XmlPullParser): Int {
                val featureAttr = resources.obtainAttributes(Xml.asAttributeSet(parser),
                        R.styleable.KeyboardLayoutSet_Feature)
                try {
                    val scriptId = featureAttr.getInt(R.styleable.KeyboardLayoutSet_Feature_supportedScript,
                            ScriptUtils.SCRIPT_UNKNOWN)
                    XmlParseUtils.checkEndTag(TAG_FEATURE, parser)
                    return scriptId
                } finally {
                    featureAttr.recycle()
                }
            }

            private fun getXmlId(resources: Resources, keyboardLayoutSetName: String?): Int {
                val packageName = resources.getResourcePackageName(
                        R.xml.keyboard_layout_set_qwerty)
                return resources.getIdentifier(keyboardLayoutSetName, "xml", packageName)
            }

            private fun getKeyboardMode(editorInfo: EditorInfo): Int {
                val inputType = editorInfo.inputType
                val variation = inputType and InputType.TYPE_MASK_VARIATION

                when (inputType and InputType.TYPE_MASK_CLASS) {
                    InputType.TYPE_CLASS_NUMBER -> return KeyboardId.MODE_NUMBER
                    InputType.TYPE_CLASS_DATETIME -> when (variation) {
                        InputType.TYPE_DATETIME_VARIATION_DATE -> return KeyboardId.MODE_DATE
                        InputType.TYPE_DATETIME_VARIATION_TIME -> return KeyboardId.MODE_TIME
                        else // InputType.TYPE_DATETIME_VARIATION_NORMAL
                        -> return KeyboardId.MODE_DATETIME
                    }
                    InputType.TYPE_CLASS_PHONE -> return KeyboardId.MODE_PHONE
                    InputType.TYPE_CLASS_TEXT -> return if (InputTypeUtils.isEmailVariation(variation)) {
                        KeyboardId.MODE_EMAIL
                    } else if (variation == InputType.TYPE_TEXT_VARIATION_URI) {
                        KeyboardId.MODE_URL
                    } else if (variation == InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE) {
                        KeyboardId.MODE_IM
                    } else if (variation == InputType.TYPE_TEXT_VARIATION_FILTER) {
                        KeyboardId.MODE_TEXT
                    } else {
                        KeyboardId.MODE_TEXT
                    }
                    else -> return KeyboardId.MODE_TEXT
                }
            }
        }
    }

    companion object {
        private val TAG = KeyboardLayoutSet::class.java.simpleName
        private val DEBUG_CACHE = false

        private val TAG_KEYBOARD_SET = "KeyboardLayoutSet"
        private val TAG_ELEMENT = "Element"
        private val TAG_FEATURE = "Feature"

        private val KEYBOARD_LAYOUT_SET_RESOURCE_PREFIX = "keyboard_layout_set_"

        // How many layouts we forcibly keep in cache. This only includes ALPHABET (default) and
        // ALPHABET_AUTOMATIC_SHIFTED layouts - other layouts may stay in memory in the map of
        // soft-references, but we forcibly cache this many alphabetic/auto-shifted layouts.
        private val FORCIBLE_CACHE_SIZE = 4
        // By construction of soft references, anything that is also referenced somewhere else
        // will stay in the cache. So we forcibly keep some references in an array to prevent
        // them from disappearing from sKeyboardCache.
        private val sForcibleKeyboardCache = arrayOfNulls<Keyboard>(FORCIBLE_CACHE_SIZE)
        private val sKeyboardCache = HashMap<KeyboardId, SoftReference<Keyboard>>()
        private val sUniqueKeysCache = UniqueKeysCache.newInstance()
        private val sScriptIdsForSubtypes = HashMap<InputMethodSubtype, Int>()

        fun onSystemLocaleChanged() {
            clearKeyboardCache()
        }

        fun onKeyboardThemeChanged() {
            clearKeyboardCache()
        }

        private fun clearKeyboardCache() {
            sKeyboardCache.clear()
            sUniqueKeysCache.clear()
        }

        fun getScriptId(resources: Resources,
                        subtype: InputMethodSubtype): Int {
            val value = sScriptIdsForSubtypes[subtype]
            if (null == value) {
                val scriptId = Builder.readScriptId(resources, subtype)
                sScriptIdsForSubtypes[subtype] = scriptId
                return scriptId
            }
            return value
        }
    }
}
