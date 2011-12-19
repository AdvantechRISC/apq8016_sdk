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

package com.android.ide.eclipse.gltrace.format;

import com.android.ide.eclipse.gldebugger.GLEnum;
import com.android.ide.eclipse.gltrace.GLProtoBuf.GLMessage.DataType;
import com.android.ide.eclipse.gltrace.GLProtoBuf.GLMessage.DataType.Type;
import com.android.ide.eclipse.gltrace.model.GLCall;

import java.util.List;
import java.util.Map;

/**
 * GLCallFormatter is used to format and create a string representation for a {@link GLCall}.
 * It is provided with a specification for all GL Functions. Using this information, each GLCall is
 * parsed and formatted appropriately for display.
 */
public class GLCallFormatter {
    private static final String GL_NO_ERROR = "GL_NO_ERROR";
    private Map<String, GLAPISpec> mAPISpecs;
    private enum DataTypeContext { CONTEXT_ARGUMENT, CONTEXT_RETURNVALUE };

    public GLCallFormatter(Map<String, GLAPISpec> specs) {
        mAPISpecs = specs;
    }

    public String formatGLCall(GLCall glCall) {
        GLAPISpec apiSpec = mAPISpecs.get(glCall.getFunction().toString());
        if (apiSpec == null) {
            return glCall.getFunction().toString();
        }

        return formatCall(apiSpec, glCall) + formatReturnValue(apiSpec, glCall);
    }

    private String formatReturnValue(GLAPISpec apiSpec, GLCall glCall) {
        if (apiSpec.getReturnValue().getDataType() == Type.VOID) {
            return "";
        }

        GLDataTypeSpec returnSpec = apiSpec.getReturnValue();
        return String.format(" = (%s) %s", returnSpec.getCType(),   //$NON-NLS-1$
                formatDataValue(glCall.getReturnValue(),
                        returnSpec,
                        DataTypeContext.CONTEXT_RETURNVALUE));
    }

    private String formatCall(GLAPISpec apiSpec, GLCall glCall) {
        return String.format("%s(%s)", apiSpec.getFunction(),       //$NON-NLS-1$
                formatArgs(glCall, apiSpec.getArgs()));
    }

    private String formatArgs(GLCall glCall, List<GLDataTypeSpec> argSpecs) {
        int sizeEstimate = 10 + argSpecs.size() * 5;
        StringBuilder sb = new StringBuilder(sizeEstimate);

        for (int i = 0; i < argSpecs.size(); i++) {
            GLDataTypeSpec argSpec = argSpecs.get(i);

            if (argSpec.getDataType() == Type.VOID && !argSpec.isPointer()) {
                sb.append("void");                                  //$NON-NLS-1$
            } else {
                sb.append(argSpec.getArgName());
                sb.append(" = ");                                   //$NON-NLS-1$
                sb.append(formatDataValue(glCall.getArg(i),
                                argSpec,
                                DataTypeContext.CONTEXT_ARGUMENT));
            }

            if (i < argSpecs.size() - 1) {
                sb.append(", ");                                    //$NON-NLS-1$
            }
        }

        return sb.toString();
    }

    private String formatDataValue(DataType var, GLDataTypeSpec typeSpec, DataTypeContext context) {
        if (typeSpec.isPointer()) {
            return formatPointer(var, typeSpec.getDataType());
        }

        switch (typeSpec.getDataType()) {
            case VOID:
                return "";
            case BOOL:
                return Boolean.toString(var.getBoolValue(0));
            case FLOAT:
                return String.format("%f", var.getFloatValue(0));   //$NON-NLS-1$
            case INT:
                return Integer.toString(var.getIntValue(0));
            case ENUM:
                if (var.getIntValue(0) == 0 && context == DataTypeContext.CONTEXT_RETURNVALUE) {
                    return GL_NO_ERROR;
                } else {
                    return GLEnum.valueOf(var.getIntValue(0)).toString();
                }
            default:
                return "(unknown type)";                            //$NON-NLS-1$
        }
    }

    private String formatPointer(DataType var, Type typeSpec) {
        if (var.getType() != typeSpec) {
            // the type of the data in the message does not match expected specification.
            // in such a case, just print the data as a pointer and don't try to interpret it.
            if (var.getIntValueCount() > 0) {
                return String.format("0x%x", var.getIntValue(0));   //$NON-NLS-1$
            } else {
                return "0x??";                                      //$NON-NLS-1$
            }
        }

        // Display as array if possible
        switch (var.getType()) {
            case BOOL:
                return var.getBoolValueList().toString();
            case FLOAT:
                return var.getFloatValueList().toString();
            case INT:
                return var.getIntValueList().toString();
            case CHAR:
                return var.getCharValueList().get(0).toStringUtf8();
        }

        // We have a pointer, but we don't have the data pointed to.
        // Just format and return the pointer (points to device memory)
        if (var.getIntValue(0) == 0) {
            return "NULL";                                          //$NON-NLS-1$
        } else {
            return String.format("0x%x", var.getIntValue(0));       //$NON-NLS-1$
        }
    }
}
