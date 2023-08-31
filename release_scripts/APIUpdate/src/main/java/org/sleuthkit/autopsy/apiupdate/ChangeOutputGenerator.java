package org.sleuthkit.autopsy.apiupdate;

import japicmp.cli.CliParser;
import japicmp.config.Options;
import japicmp.model.*;
import japicmp.model.JApiAnnotationElementValue.Type;
import static japicmp.model.JApiChangeStatus.MODIFIED;
import static japicmp.model.JApiChangeStatus.NEW;
import static japicmp.model.JApiChangeStatus.REMOVED;
import static japicmp.model.JApiChangeStatus.UNCHANGED;
import japicmp.output.OutputFilter;
import japicmp.output.OutputGenerator;
import javassist.bytecode.annotation.MemberValue;

import java.util.Comparator;
import java.util.List;

import static japicmp.util.GenericTemplateHelper.haveGenericTemplateInterfacesChanges;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.apiupdate.ApiChangeDTO.FieldChangeDTO;
import org.sleuthkit.autopsy.apiupdate.ApiChangeDTO.InterfaceChangeDTO;
import org.sleuthkit.autopsy.apiupdate.ApiChangeDTO.SuperclassChangeDTO;

// taken from https://raw.githubusercontent.com/siom79/japicmp/master/japicmp/src/main/java/japicmp/output/stdout/StdoutOutputGenerator.java
public class ChangeOutputGenerator extends OutputGenerator<ApiChangeDTO> {

    static final String NO_CHANGES = "No changes.";
    static final String WARNING = "WARNING";

    public ChangeOutputGenerator(Options options, List<JApiClass> jApiClasses) {
        super(options, jApiClasses);
    }

    @Override
    public String generate() {
        OutputFilter outputFilter = new OutputFilter(options);
        outputFilter.filter(jApiClasses);
        StringBuilder sb = new StringBuilder();
        sb.append(options.getDifferenceDescription()).append('\n');
        if (options.getIgnoreMissingClasses().isIgnoreAllMissingClasses()) {
            sb.append(WARNING).append(": You are using the option '").append(CliParser.IGNORE_MISSING_CLASSES)
                    .append("', i.e. superclasses and interfaces that could not be found on the classpath are ignored.")
                    .append(" Hence changes caused by these superclasses and interfaces are not reflected in the output.\n");
        } else if (options.getIgnoreMissingClasses().getIgnoreMissingClassRegularExpression().size() > 0) {
            sb.append(WARNING).append(": You have ignored certain classes, i.e. superclasses and interfaces that could not ")
                    .append("be found on the classpath are ignored. Hence changes caused by these superclasses and interfaces are not reflected in the output.\n");
        }
        if (jApiClasses.size() > 0) {
            for (JApiClass jApiClass : jApiClasses) {
                processClass(sb, jApiClass);
                processConstructors(sb, jApiClass);
                processMethods(sb, jApiClass);
                processAnnotations(sb, jApiClass, 1);
            }
        } else {
            sb.append(NO_CHANGES);
        }
        return sb.toString();
    }

    private void processAnnotations(StringBuilder sb, JApiHasAnnotations jApiClass, int numberofTabs) {
        List<JApiAnnotation> annotations = jApiClass.getAnnotations();
        for (JApiAnnotation jApiAnnotation : annotations) {
            appendAnnotation(sb, signs(jApiAnnotation), jApiAnnotation, numberofTabs);
            List<JApiAnnotationElement> elements = jApiAnnotation.getElements();
            for (JApiAnnotationElement jApiAnnotationElement : elements) {
                appendAnnotationElement(sb, signs(jApiAnnotationElement), jApiAnnotationElement, numberofTabs + 1);
            }
        }
    }

    private void processConstructors(StringBuilder sb, JApiClass jApiClass) {
        List<JApiConstructor> constructors = jApiClass.getConstructors();
        for (JApiConstructor jApiConstructor : constructors) {
            appendBehavior(sb, signs(jApiConstructor), jApiConstructor, "CONSTRUCTOR:");
            processAnnotations(sb, jApiConstructor, 2);
            processExceptions(sb, jApiConstructor, 2);
            processGenericTemplateChanges(sb, jApiConstructor, 2);
        }
    }

    private void processMethods(StringBuilder sb, JApiClass jApiClass) {
        List<JApiMethod> methods = jApiClass.getMethods();
        for (JApiMethod jApiMethod : methods) {
            appendBehavior(sb, signs(jApiMethod), jApiMethod, "METHOD:");
            processAnnotations(sb, jApiMethod, 2);
            processExceptions(sb, jApiMethod, 2);
            processGenericTemplateChanges(sb, jApiMethod, 2);
        }
    }

    private void processExceptions(StringBuilder sb, JApiBehavior jApiBehavior, int indent) {
        for (JApiException exception : jApiBehavior.getExceptions()) {
            appendException(sb, signs(exception), exception, indent);
        }
    }

    private void appendException(StringBuilder sb, String signs, JApiException jApiException, int indent) {
        sb.append(tabs(indent)).append(signs).append(" ").append(jApiException.getChangeStatus()).append(" EXCEPTION: ").append(jApiException.getName()).append("\n");
    }

    private void processClass(StringBuilder sb, JApiClass jApiClass) {
        appendClass(sb, signs(jApiClass), jApiClass);
    }

    private void appendBehavior(StringBuilder sb, String signs, JApiBehavior jApiBehavior, String classMemberType) {
        sb.append("\t").append(signs).append(" ").append(jApiBehavior.getChangeStatus()).append(" ").append(classMemberType).append(" ")
                .append(accessModifierAsString(jApiBehavior)).append(abstractModifierAsString(jApiBehavior)).append(staticModifierAsString(jApiBehavior))
                .append(finalModifierAsString(jApiBehavior)).append(syntheticModifierAsString(jApiBehavior)).append(bridgeModifierAsString(jApiBehavior))
                .append(returnType(jApiBehavior)).append(jApiBehavior.getName()).append("(");
        int paramCount = 0;
        for (JApiParameter jApiParameter : jApiBehavior.getParameters()) {
            if (paramCount > 0) {
                sb.append(", ");
            }
            sb.append(jApiParameter.getType());
            appendGenericTypes(sb, jApiParameter);
            paramCount++;
        }
        sb.append(")\n");
    }

    private void appendGenericTypes(StringBuilder sb, JApiHasGenericTypes jApiHasGenericTypes) {
        if (!jApiHasGenericTypes.getNewGenericTypes().isEmpty() || !jApiHasGenericTypes.getOldGenericTypes().isEmpty()) {
            if (jApiHasGenericTypes instanceof JApiCompatibility) {
                List<JApiCompatibilityChange> compatibilityChanges = ((JApiCompatibility) jApiHasGenericTypes).getCompatibilityChanges();
                appendGenericTypesForCompatibilityChanges(sb, jApiHasGenericTypes, compatibilityChanges);
            }
        }
    }

    private void appendGenericTypesForCompatibilityChanges(StringBuilder sb, JApiHasGenericTypes jApiHasGenericTypes, List<JApiCompatibilityChange> compatibilityChanges) {
        if (compatibilityChanges.contains(JApiCompatibilityChange.METHOD_PARAMETER_GENERICS_CHANGED)
                || compatibilityChanges.contains(JApiCompatibilityChange.METHOD_RETURN_TYPE_GENERICS_CHANGED)
                || compatibilityChanges.contains(JApiCompatibilityChange.FIELD_GENERICS_CHANGED)
                || compatibilityChanges.contains(JApiCompatibilityChange.CLASS_GENERIC_TEMPLATE_GENERICS_CHANGED)) {
            appendGenericTypes(sb, false, jApiHasGenericTypes.getNewGenericTypes());
            appendGenericTypes(sb, true, jApiHasGenericTypes.getOldGenericTypes());
        } else {
            if (!jApiHasGenericTypes.getNewGenericTypes().isEmpty()) {
                appendGenericTypes(sb, false, jApiHasGenericTypes.getNewGenericTypes());
            }
            if (!jApiHasGenericTypes.getOldGenericTypes().isEmpty()) {
                appendGenericTypes(sb, false, jApiHasGenericTypes.getOldGenericTypes());
            }
        }
    }

    private void appendGenericTypes(StringBuilder sb, boolean withChangeInParenthesis, List<JApiGenericType> genericTypes) {
        if (!genericTypes.isEmpty()) {
            if (withChangeInParenthesis) {
                sb.append("(<- ");
            }
            sb.append("<");
            int count = 0;
            for (JApiGenericType genericType : genericTypes) {
                if (count > 0) {
                    sb.append(",");
                }
                appendGenericType(sb, genericType);
                if (!genericType.getGenericTypes().isEmpty()) {
                    appendGenericTypes(sb, false, genericType.getGenericTypes());
                }
                count++;
            }
            sb.append(">");
            if (withChangeInParenthesis) {
                sb.append(")");
            }
        }
    }

    private void appendGenericType(StringBuilder sb, JApiGenericType jApiGenericType) {
        if (jApiGenericType.getGenericWildCard() == JApiGenericType.JApiGenericWildCard.NONE) {
            sb.append(jApiGenericType.getType());
        } else if (jApiGenericType.getGenericWildCard() == JApiGenericType.JApiGenericWildCard.UNBOUNDED) {
            sb.append("?");
        } else if (jApiGenericType.getGenericWildCard() == JApiGenericType.JApiGenericWildCard.EXTENDS) {
            sb.append("? extends ").append(jApiGenericType.getType());
        } else if (jApiGenericType.getGenericWildCard() == JApiGenericType.JApiGenericWildCard.SUPER) {
            sb.append("? super ").append(jApiGenericType.getType());
        }
    }

    private String returnType(JApiBehavior jApiBehavior) {
        StringBuilder sb = new StringBuilder();
        if (jApiBehavior instanceof JApiMethod) {
            JApiMethod method = (JApiMethod) jApiBehavior;
            JApiReturnType jApiReturnType = method.getReturnType();
            if (jApiReturnType.getChangeStatus() == JApiChangeStatus.UNCHANGED) {
                sb.append(jApiReturnType.getNewReturnType());
                appendGenericTypes(sb, jApiReturnType);
                sb.append(" ");
            } else if (jApiReturnType.getChangeStatus() == JApiChangeStatus.MODIFIED) {
                sb.append(jApiReturnType.getNewReturnType());
                appendGenericTypes(sb, jApiReturnType);
                sb.append(" (<-");
                sb.append(jApiReturnType.getOldReturnType());
                appendGenericTypes(sb, jApiReturnType);
                sb.append(") ");
            } else if (jApiReturnType.getChangeStatus() == JApiChangeStatus.NEW) {
                sb.append(jApiReturnType.getNewReturnType());
                appendGenericTypes(sb, jApiReturnType);
                sb.append(" ");
            } else {
                sb.append(jApiReturnType.getOldReturnType());
                appendGenericTypes(sb, jApiReturnType);
                sb.append(" ");
            }
        }
        return sb.toString();
    }

    private void appendAnnotation(StringBuilder sb, String signs, JApiAnnotation jApiAnnotation, int numberOfTabs) {
        sb.append(String.format("%s%s %s ANNOTATION: %s\n", tabs(numberOfTabs), signs, jApiAnnotation.getChangeStatus(), jApiAnnotation.getFullyQualifiedName()));
    }

    private void appendAnnotationElement(StringBuilder sb, String signs, JApiAnnotationElement jApiAnnotationElement, int numberOfTabs) {
        sb.append(String.format("%s%s %s ELEMENT: %s=", tabs(numberOfTabs), signs, jApiAnnotationElement.getChangeStatus(), jApiAnnotationElement.getName()));
        Optional<MemberValue> oldValue = jApiAnnotationElement.getOldValue();
        Optional<MemberValue> newValue = jApiAnnotationElement.getNewValue();
        if (oldValue.isPresent() && newValue.isPresent()) {
            if (jApiAnnotationElement.getChangeStatus() == JApiChangeStatus.UNCHANGED) {
                sb.append(elementValueList2String(jApiAnnotationElement.getNewElementValues()));
            } else if (jApiAnnotationElement.getChangeStatus() == JApiChangeStatus.REMOVED) {
                sb.append(String.format("%s (-)", elementValueList2String(jApiAnnotationElement.getOldElementValues())));
            } else if (jApiAnnotationElement.getChangeStatus() == JApiChangeStatus.NEW) {
                sb.append(String.format("%s (+)", elementValueList2String(jApiAnnotationElement.getNewElementValues())));
            } else if (jApiAnnotationElement.getChangeStatus() == JApiChangeStatus.MODIFIED) {
                sb.append(String.format("%s (<- %s)", elementValueList2String(jApiAnnotationElement.getNewElementValues()), elementValueList2String(jApiAnnotationElement.getOldElementValues())));
            }
        } else if (!oldValue.isPresent() && newValue.isPresent()) {
            sb.append(String.format("%s (+)", elementValueList2String(jApiAnnotationElement.getNewElementValues())));
        } else if (oldValue.isPresent() && !newValue.isPresent()) {
            sb.append(String.format("%s (-)", elementValueList2String(jApiAnnotationElement.getOldElementValues())));
        } else {
            sb.append(" n.a.");
        }
        sb.append("\n");
    }

    private void appendClass(StringBuilder sb, String signs, JApiClass jApiClass) {
        sb.append(signs).append(" ").append(jApiClass.getChangeStatus()).append(" ")
                .append(processClassType(jApiClass)).append(": ")
                .append(accessModifierAsString(jApiClass))
                .append(abstractModifierAsString(jApiClass))
                .append(staticModifierAsString(jApiClass))
                .append(finalModifierAsString(jApiClass))
                .append(syntheticModifierAsString(jApiClass))
                .append(jApiClass.getFullyQualifiedName()).append(" ")
                .append(javaObjectSerializationStatus(jApiClass)).append("\n");

        processGenericTemplateChanges(sb, jApiClass, 1);
        processInterfaceChanges(sb, jApiClass);
        processSuperclassChanges(sb, jApiClass);
        processFieldChanges(sb, jApiClass);
    }

    private String getClassString(JApiClass clz, boolean oldClass) {
        if (jApiClass.getChangeStatus() == NEW && oldClass || jApiClass.getChangeStatus() == REMOVED && !oldClass) {
            return null;
        }

        String accessModifier = str(get(clz.getAccessModifier(), oldClass).orElse(null));
        String abstractModifier = str(get(clz.getAbstractModifier(), oldClass).orElse(null));
        String staticModifier = str(get(clz.getStaticModifier(), oldClass).orElse(null));
        String finalModifier = str(get(clz.getFinalModifier(), oldClass).orElse(null));
        String syntheticModifier = str(get(clz.getSyntheticModifier(), oldClass).orElse(null));

        String name = clz.getFullyQualifiedName();

        String genericModifier = strOpt(get(clz.getGenericTemplates();

        String implementsModifier = TBD;
        String extendsModifier = TBD;

        return Stream.of(
                accessModifier,
                abstractModifier,
                staticModifier,
                finalModifier,
                syntheticModifier,
                "class",
                name + (StringUtils.isBlank(genericModifier) ? "" : genericModifier),
                implementsModifier,
                extendsModifier)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining(" "));
    }

//    private List<GenericTemplateChangeDTO> getGenericTemplateChanges(JApiHasGenericTemplates jApiHasGenericTemplates) {
//        return jApiHasGenericTemplates.getGenericTemplates().stream()
//                .filter(template -> template.getChangeStatus() != JApiChangeStatus.UNCHANGED)
//                .map(template -> new GenericTemplateChangeDTO())
//                .collect(Collectors.toList());
//        
//        if (!genericTemplates.isEmpty()) {
//            sb.append(tabs(numberOfTabs)).append("GENERIC TEMPLATES: ");
//            genericTemplates.sort(Comparator.comparing(JApiGenericTemplate::getName));
//            int count = 0;
//            for (JApiGenericTemplate jApiGenericTemplate : genericTemplates) {
//                if (count > 0) {
//                    sb.append(", ");
//                }
//                count++;
//                sb.append(signs(jApiGenericTemplate));
//                if (sb.charAt(sb.length() - 1) != ' ') {
//                    sb.append(" ");
//                }
//                sb.append(jApiGenericTemplate.getName()).append(":");
//                JApiChangeStatus changeStatus = jApiGenericTemplate.getChangeStatus();
//                if (changeStatus == JApiChangeStatus.NEW || changeStatus == JApiChangeStatus.UNCHANGED) {
//                    sb.append(jApiGenericTemplate.getNewType());
//                    if (jApiGenericTemplate instanceof JApiCompatibility) {
//                        appendGenericTypesForCompatibilityChanges(sb, jApiGenericTemplate, ((JApiCompatibility) jApiHasGenericTemplates).getCompatibilityChanges());
//                    }
//                } else if (changeStatus == JApiChangeStatus.REMOVED) {
//                    sb.append(jApiGenericTemplate.getOldType());
//                    if (jApiGenericTemplate instanceof JApiCompatibility) {
//                        appendGenericTypesForCompatibilityChanges(sb, jApiGenericTemplate, ((JApiCompatibility) jApiHasGenericTemplates).getCompatibilityChanges());
//                    }
//                } else {
//                    sb.append(jApiGenericTemplate.getNewType());
//                    appendGenericTypes(sb, false, jApiGenericTemplate.getNewGenericTypes());
//                    sb.append(" (<-").append(jApiGenericTemplate.getOldType());
//                    appendGenericTypes(sb, false, jApiGenericTemplate.getOldGenericTypes());
//                    sb.append(")");
//                }
//                if (!jApiGenericTemplate.getOldInterfaceTypes().isEmpty() || !jApiGenericTemplate.getNewInterfaceTypes().isEmpty()) {
//                    if (haveGenericTemplateInterfacesChanges(jApiGenericTemplate.getOldInterfaceTypes(), jApiGenericTemplate.getNewInterfaceTypes())) {
//                        appendGenericTemplatesInterfaces(sb, jApiGenericTemplate, false, true);
//                        sb.append(" (<-");
//                        appendGenericTemplatesInterfaces(sb, jApiGenericTemplate, true, false);
//                        sb.append(")");
//                    } else {
//                        appendGenericTemplatesInterfaces(sb, jApiGenericTemplate, false, true);
//                    }
//                }
//            }
//            sb.append("\n");
//        }
//    }
//    private void appendGenericTemplatesInterfaces(StringBuilder sb, JApiGenericTemplate jApiGenericTemplate, boolean printOld, boolean printNew) {
//        if (printOld) {
//            for (JApiGenericType jApiGenericType : jApiGenericTemplate.getOldInterfaceTypes()) {
//                sb.append(" & ");
//                sb.append(jApiGenericType.getType());
//                appendGenericTypes(sb, false, jApiGenericType.getGenericTypes());
//            }
//        }
//        if (printNew) {
//            for (JApiGenericType jApiGenericType : jApiGenericTemplate.getNewInterfaceTypes()) {
//                sb.append(" & ");
//                sb.append(jApiGenericType.getType());
//                appendGenericTypes(sb, false, jApiGenericType.getGenericTypes());
//            }
//        }
//    }
//    private String str(JApiHasGenericTemplates jApiHasGenericTemplates) {
//        jApiHasGenericTemplates.getGenericTemplates().get(0).g
//    }
    private FieldChangeDTO getFieldChange(JApiField field) {
        return new FieldChangeDTO(
                field.getChangeStatus(),
                Optional.ofNullable(getFieldString(field, true)),
                Optional.ofNullable(getFieldString(field, false)));
    }

    private String getFieldString(JApiField field, boolean oldField) {
        if (field.getChangeStatus() == NEW && oldField || field.getChangeStatus() == REMOVED && !oldField) {
            return null;
        }

        String accessModifier = str(get(field.getAccessModifier(), oldField).orElse(null));
        String staticModifier = str(get(field.getStaticModifier(), oldField).orElse(null));
        String finalModifier = str(get(field.getFinalModifier(), oldField).orElse(null));
        String syntheticModifier = str(get(field.getSyntheticModifier(), oldField).orElse(null));
        String fieldType = strOpt(field.getType(), oldField).orElse("");
        String name = field.getName();

        return Stream.of(accessModifier, staticModifier, finalModifier, syntheticModifier, fieldType, name)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining(" "));
    }

    private String str(AccessModifier modifier) {
        if (modifier == null || modifier == AccessModifier.PACKAGE_PROTECTED) {
            return "";
        } else {
            return modifier.name().toLowerCase();
        }
    }

    private String str(StaticModifier modifier) {
        return (modifier == StaticModifier.STATIC)
                ? "static"
                : "";
    }

    private String str(FinalModifier modifier) {
        return (modifier == FinalModifier.FINAL)
                ? "final"
                : "";
    }

    private String str(SyntheticModifier modifier) {
        return (modifier == SyntheticModifier.SYNTHETIC)
                ? "synthetic"
                : "";
    }

    private String str(AbstractModifier modifier) {
        return (modifier == AbstractModifier.ABSTRACT)
                ? "abstract"
                : "";
    }

    private Optional<String> strOpt(JApiType tp, boolean oldField) {
        return oldField ? convert(tp.getOldTypeOptional()) : convert(tp.getNewTypeOptional());
    }

    private <T> Optional<T> get(JApiModifier<T> modifier, boolean oldField) {
        return oldField ? convert(modifier.getOldModifier()) : convert(modifier.getNewModifier());
    }

//    private Optional<SuperclassChangeDTO> getSuperClassChange(JApiSuperclass jApiSuperclass) {
//        return jApiSuperclass.getChangeStatus() != JApiChangeStatus.UNCHANGED
//                ? Optional.ofNullable(new SuperclassChangeDTO(
//                        convert(jApiSuperclass.getOldSuperclassName()),
//                        convert(jApiSuperclass.getNewSuperclassName())))
//                : Optional.empty();
//    }
//
//    private List<InterfaceChangeDTO> getInterfaceChanges(JApiClass jApiClass) {
//        return jApiClass.getInterfaces().stream()
//                .filter(intfc -> intfc.getChangeStatus() != JApiChangeStatus.UNCHANGED)
//                .map(intfc -> new InterfaceChangeDTO(intfc.getChangeStatus(), intfc.getFullyQualifiedName()))
//                .collect(Collectors.toList());
//    }
    private <T> Optional<T> convert(japicmp.util.Optional<T> apiOpt) {
        T val = apiOpt.or((T) null);
        return java.util.Optional.ofNullable(val);
    }

//
//    private void processClassFileFormatVersionChanges(StringBuilder sb, JApiClass jApiClass) {
//        JApiClassFileFormatVersion classFileFormatVersion = jApiClass.getClassFileFormatVersion();
//        sb.append(tabs(1))
//                .append(signs(classFileFormatVersion))
//                .append(" CLASS FILE FORMAT VERSION: ");
//        if (classFileFormatVersion.getMajorVersionNew() != -1 && classFileFormatVersion.getMinorVersionNew() != -1) {
//            sb.append(classFileFormatVersion.getMajorVersionNew()).append(".").append(classFileFormatVersion.getMinorVersionNew());
//        } else {
//            sb.append("n.a.");
//        }
//        sb.append(" <- ");
//        if (classFileFormatVersion.getMajorVersionOld() != -1 && classFileFormatVersion.getMinorVersionOld() != -1) {
//            sb.append(classFileFormatVersion.getMajorVersionOld()).append(".").append(classFileFormatVersion.getMinorVersionOld());
//        } else {
//            sb.append("n.a.");
//        }
//        sb.append("\n");
//    }
//    private String processClassType(JApiClass jApiClass) {
//        JApiClassType classType = jApiClass.getClassType();
//        switch (classType.getChangeStatus()) {
//            case NEW:
//                return classType.getNewType();
//            case REMOVED:
//                return classType.getOldType();
//            case MODIFIED:
//                return classType.getNewType() + " (<- " + classType.getOldType() + ") ";
//            case UNCHANGED:
//                return classType.getOldType();
//        }
//        return "n.a.";
//    }
//
//    // TODO do we need this
//    private String getJavaObjectSerializationStatus(JApiClass jApiClass) {
//        return jApiClass.getJavaObjectSerializationCompatible().getDescription();
//    }
//    private String elementValueList2String(List<JApiAnnotationElementValue> values) {
//        StringBuilder sb = new StringBuilder();
//        for (JApiAnnotationElementValue value : values) {
//            if (sb.length() > 0) {
//                sb.append(",");
//            }
//            if (value.getName().isPresent()) {
//                sb.append(value.getName().get()).append("=");
//            }
//            if (value.getType() != Type.Array && value.getType() != Type.Annotation) {
//                if (value.getType() == Type.Enum) {
//                    sb.append(value.getFullyQualifiedName()).append(".").append(value.getValueString());
//                } else {
//                    sb.append(value.getValueString());
//                }
//            } else {
//                if (value.getType() == Type.Array) {
//                    sb.append("{").append(elementValueList2String(value.getValues())).append("}");
//                } else if (value.getType() == Type.Annotation) {
//                    sb.append("@").append(value.getFullyQualifiedName()).append("(").append(elementValueList2String(value.getValues())).append(")");
//                }
//            }
//        }
//        return sb.toString();
//    }
//
//    private String tabs(int numberOfTabs) {
//        if (numberOfTabs <= 0) {
//            return "";
//        } else if (numberOfTabs == 1) {
//            return "\t";
//        } else if (numberOfTabs == 2) {
//            return "\t\t";
//        } else {
//            StringBuilder sb = new StringBuilder();
//            for (int i = 0; i < numberOfTabs; i++) {
//                sb.append("\t");
//            }
//            return sb.toString();
//        }
//    }
//    private String signs(JApiHasChangeStatus hasChangeStatus) {
//        JApiChangeStatus changeStatus = hasChangeStatus.getChangeStatus();
//        String retVal = "???";
//        switch (changeStatus) {
//            case UNCHANGED:
//                retVal = "===";
//                break;
//            case NEW:
//                retVal = "+++";
//                break;
//            case REMOVED:
//                retVal = "---";
//                break;
//            case MODIFIED:
//                retVal = "***";
//                break;
//        }
//        boolean binaryCompatible = true;
//        boolean sourceCompatible = true;
//        if (hasChangeStatus instanceof JApiCompatibility) {
//            JApiCompatibility jApiCompatibility = (JApiCompatibility) hasChangeStatus;
//            binaryCompatible = jApiCompatibility.isBinaryCompatible();
//            sourceCompatible = jApiCompatibility.isSourceCompatible();
//        }
//        if (binaryCompatible) {
//            if (sourceCompatible) {
//                retVal += " ";
//            } else {
//                retVal += "*";
//            }
//        } else {
//            retVal += "!";
//        }
//        return retVal;
//    } 
//
//    private String bridgeModifierAsString(JApiHasBridgeModifier modifier) {
//        JApiModifier<BridgeModifier> bridgeModifier = modifier.getBridgeModifier();
//        return modifierAsString(bridgeModifier, BridgeModifier.NON_BRIDGE);
//    }
//    private <T> String modifierAsString(JApiModifier<T> modifier, T notPrintValue) {
//        if (modifier.getOldModifier().isPresent() && modifier.getNewModifier().isPresent()) {
//            if (modifier.getChangeStatus() == JApiChangeStatus.MODIFIED) {
//                return modifier.getNewModifier().get() + " (<- " + modifier.getOldModifier().get() + ") ";
//            } else if (modifier.getChangeStatus() == JApiChangeStatus.NEW) {
//                if (modifier.getNewModifier().get() != notPrintValue) {
//                    return modifier.getNewModifier().get() + "(+) ";
//                }
//            } else if (modifier.getChangeStatus() == JApiChangeStatus.REMOVED) {
//                if (modifier.getOldModifier().get() != notPrintValue) {
//                    return modifier.getOldModifier().get() + "(-) ";
//                }
//            } else {
//                if (modifier.getNewModifier().get() != notPrintValue) {
//                    return modifier.getNewModifier().get() + " ";
//                }
//            }
//        } else if (modifier.getOldModifier().isPresent()) {
//            if (modifier.getOldModifier().get() != notPrintValue) {
//                return modifier.getOldModifier().get() + "(-) ";
//            }
//        } else if (modifier.getNewModifier().isPresent()) {
//            if (modifier.getNewModifier().get() != notPrintValue) {
//                return modifier.getNewModifier().get() + "(+) ";
//            }
//        }
//        return "";
//    }
//    private String fieldTypeChangeAsString(JApiField field) {
//        JApiType type = field.getType();
//        if (type.getOldTypeOptional().isPresent() && type.getNewTypeOptional().isPresent()) {
//            if (type.getChangeStatus() == JApiChangeStatus.MODIFIED) {
//                return type.getNewTypeOptional().get() + " (<- " + type.getOldTypeOptional().get() + ")";
//            } else if (type.getChangeStatus() == JApiChangeStatus.NEW) {
//                return type.getNewTypeOptional().get() + "(+)";
//            } else if (type.getChangeStatus() == JApiChangeStatus.REMOVED) {
//                return type.getOldTypeOptional().get() + "(-)";
//            } else {
//                return type.getNewTypeOptional().get();
//            }
//        } else if (type.getOldTypeOptional().isPresent() && !type.getNewTypeOptional().isPresent()) {
//            return type.getOldTypeOptional().get();
//        } else if (!type.getOldTypeOptional().isPresent() && type.getNewTypeOptional().isPresent()) {
//            return type.getNewTypeOptional().get();
//        }
//        return "n.a.";
//    }
}
