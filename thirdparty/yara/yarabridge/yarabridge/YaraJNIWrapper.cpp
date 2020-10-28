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
* Class:     org_sleuthkit_autopsy_yara_YaraJNIWrapper
* Method:    FindRuleMatch
* Signature: (Ljava/lang/String;[B)Ljava/util/List;
*/
JNIEXPORT jobject JNICALL Java_org_sleuthkit_autopsy_yara_YaraJNIWrapper_FindRuleMatch
(JNIEnv * env, jclass cls, jstring compiledRulePath, jbyteArray fileByteArray) {
	
	char errorMessage[256];
	const char *nativeString = env->GetStringUTFChars(compiledRulePath, 0);
	jobject resultList = NULL;
	
	int result;
	if ((result = yr_initialize()) != ERROR_SUCCESS) {
		sprintf_s(errorMessage, "libyara initialization error (%d)\n", result);
		throwException(env, errorMessage);
		return resultList;
	}

	while (1) {
		YR_RULES *rules = NULL;
		if ((result = yr_rules_load(nativeString, &rules)) != ERROR_SUCCESS) {
			sprintf_s(errorMessage, "Failed to load compiled yara rules (%d)\n", result);
			throwException(env, errorMessage);
			break;
		}

		boolean isCopy;
		int byteArrayLength = env->GetArrayLength(fileByteArray);
		if (byteArrayLength == 0) {
			throwException(env, "Unable to scan for matches. File byte array size was 0.");
			break;
		}

		jbyte* nativeByteArray = env->GetByteArrayElements(fileByteArray, &isCopy);
		int flags = 0;
		std::vector<std::string> scanResults;

		result = yr_rules_scan_mem(rules, (unsigned char*)nativeByteArray, byteArrayLength, flags, callback, &scanResults, 1000000);
		env->ReleaseByteArrayElements(fileByteArray, nativeByteArray, 0);

		if (result != ERROR_SUCCESS) {
			sprintf_s(errorMessage, "Yara file scan failed (%d)\n", result);
			throwException(env, errorMessage);
			break;
		}
		
		resultList = createArrayList(env, scanResults);
		break;
	}

	env->ReleaseStringUTFChars(compiledRulePath, nativeString);
	yr_finalize();

	return resultList;

}