/*
** YaraBridge
**
** Brian Carrier [carrier <at> sleuthkit [dot] org]
** Copyright (c) 2010-2018 Brian Carrier.  All Rights reserved
**
** This software is distributed under the Common Public License 1.0
**
*/

#include<stdio.h>
#include<jni.h>
#include "YaraJNIWrapper.h"
#include "yara.h"
#include <vector>
#include <algorithm>
#include <iostream>
#include <numeric>

using std::string;
using std::vector;

/*
	Callback method to be passed to yr_rules_scan_mem method.
	user_data is expected to be a pointer to a string vector.
*/
static int callback(
	YR_SCAN_CONTEXT* context,
	int message,
	void* message_data,
	void* user_data)
{
	if (message == CALLBACK_MSG_RULE_MATCHING) {
		YR_RULE *rule = (YR_RULE *)message_data;

		((std::vector<std::string>*)user_data)->push_back(rule->identifier);
	}
	return CALLBACK_CONTINUE;
}


/*
	Throw a new instance of YaraWrapperException with the given message.

	Unlike in JAVA throwing this exception will not stop the execution
	of the method from which it is thrown. 
*/
static void throwException(JNIEnv *env, char * msg) {
	jclass cls;

	cls = env->FindClass("org/sleuthkit/autopsy/yara/YaraWrapperException");
	if (cls == NULL) {
		fprintf(stderr, "Failed to throw YaraWrapperException, cannot find class\n");
		return;
	}

	env->ThrowNew(cls, msg);

}

/*
	Generic method that will create a Java ArrayList object populating it with 
	the strings from the given vector.
*/
jobject createArrayList(JNIEnv *env, std::vector<std::string> vector) {
	jclass cls_arrayList = env->FindClass("java/util/ArrayList");
	jmethodID constructor = env->GetMethodID(cls_arrayList, "<init>", "(I)V");
	jmethodID method_add = env->GetMethodID(cls_arrayList, "add", "(Ljava/lang/Object;)Z");

	jobject list = env->NewObject(cls_arrayList, constructor, vector.size());

	for (std::string str : vector) {
		jstring element = env->NewStringUTF(str.c_str());
		env->CallBooleanMethod(list, method_add, element);
		env->DeleteLocalRef(element);
	}

	return list;
}

/*
	Loads the compiled rules file returning a YARA error code.
	Throws a java exeception if there are any issues.
*/
int loadRuleFile(JNIEnv * env, jstring compiledRulePath, YR_RULES **rules) {
	char errorMessage[256];
	const char *nativeString = env->GetStringUTFChars(compiledRulePath, 0);
	int result = yr_rules_load(nativeString, rules);

	if (result != ERROR_SUCCESS) {
		sprintf_s(errorMessage, "Failed to load compiled yara rule %s (error code = %d)\n", nativeString, result);
		throwException(env, errorMessage);
	}

	env->ReleaseStringUTFChars(compiledRulePath, nativeString);

	return result;
}

/*
	Initalize the YARA library, if needed. yr_initialize only needs to be called once.
*/
int initalizeYaraLibrary(JNIEnv * env) {
	static int library_initalized = 0;
	char errorMessage[256];
	int result = ERROR_SUCCESS;
	if (library_initalized == 0) {
		if ((result = yr_initialize()) != ERROR_SUCCESS) {
			sprintf_s(errorMessage, "libyara initialization error (%d)\n", result);
			throwException(env, errorMessage);
		}
		library_initalized = 1;
	}

	return result;
}

/*
* Class:     org_sleuthkit_autopsy_yara_YaraJNIWrapper
* Method:    FindRuleMatch
* Signature: (Ljava/lang/String;[B)Ljava/util/List;
*/
JNIEXPORT jobject JNICALL Java_org_sleuthkit_autopsy_yara_YaraJNIWrapper_findRuleMatch
(JNIEnv * env, jclass cls, jstring compiledRulePath, jbyteArray fileByteArray, jint byteArrayLength, jint timeoutSec) {
	
	char errorMessage[256];
	jobject resultList = NULL;
	int result;
	YR_RULES *rules = NULL;

	if ((result = initalizeYaraLibrary(env)) != ERROR_SUCCESS) {
		return resultList;
	}

	
	while (1) {
		if((result = loadRuleFile(env, compiledRulePath, &rules)) != ERROR_SUCCESS) {
			break;
		}

		if (byteArrayLength == 0) {
			throwException(env, "Unable to scan for matches. File byte array size was 0.");
			break;
		}

		boolean isCopy;
		jbyte* nativeByteArray = env->GetByteArrayElements(fileByteArray, &isCopy);
		std::vector<std::string> scanResults;

		result = yr_rules_scan_mem(rules, (unsigned char*)nativeByteArray, byteArrayLength, 0, callback, &scanResults, timeoutSec);
		env->ReleaseByteArrayElements(fileByteArray, nativeByteArray, 0);

		if (result != ERROR_SUCCESS) {
			if (result == ERROR_SCAN_TIMEOUT) {
				sprintf_s(errorMessage, "Yara file scan timed out");
			}
			else {
				sprintf_s(errorMessage, "Yara file scan failed (%d)\n", result);
			}
			throwException(env, errorMessage);
			break;
		}
		
		resultList = createArrayList(env, scanResults);
		break;
	}

	if (rules != NULL) {
		yr_rules_destroy(rules);
	}

	return resultList;

}

/*
* Class:     org_sleuthkit_autopsy_yara_YaraJNIWrapper
* Method:    findRuleMatchFile
* Signature: (Ljava/lang/String;Ljava/lang/String;)Ljava/util/List;
*/
JNIEXPORT jobject JNICALL Java_org_sleuthkit_autopsy_yara_YaraJNIWrapper_findRuleMatchFile
(JNIEnv * env, jclass cls, jstring compiledRulePath, jstring filePath, jint timeoutSec) {

	char errorMessage[256];
	jobject resultList = NULL;
	int result;
	YR_RULES *rules = NULL;

	if ((result = initalizeYaraLibrary(env)) != ERROR_SUCCESS) {
		return resultList;
	}


	while (1) {
		if ((result = loadRuleFile(env, compiledRulePath, &rules)) != ERROR_SUCCESS) {
			break;
		}

		std::vector<std::string> scanResults;
		const char *nativeString = env->GetStringUTFChars(filePath, 0);

		result = yr_rules_scan_file(rules, nativeString, 0, callback, &scanResults, timeoutSec);

		if (result != ERROR_SUCCESS) {
			if (result == ERROR_SCAN_TIMEOUT) {
				sprintf_s(errorMessage, "Yara file scan timed out on file %s", nativeString);
			}
			else {
				sprintf_s(errorMessage, "Yara file scan failed (%d)\n", result);
			}
			throwException(env, errorMessage);
			break;
		}

		resultList = createArrayList(env, scanResults);
		break;
	}

	if (rules != NULL) {
		yr_rules_destroy(rules);
	}

	return resultList;
}