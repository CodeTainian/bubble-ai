package com.bubble.bubbleaiapp.core.saver;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.bubble.bubbleai.ai.model.enums.CodeGenTypeEnum;
import com.bubble.bubbleai.constant.AppConstant;
import com.bubble.bubbleai.exception.BusinessException;
import com.bubble.bubbleai.exception.ErrorCode;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * 抽象代码文件保存器 --模板方法模式
 * 模板模式（Template Method Pattern）是一种行为型设计模式，用于在一个方法中定义算法的骨架，而将某些具体步骤的实现延迟到子类中。
 * 🧩 核心思想
 * 在父类中定义一个算法的通用执行流程（模板方法），其中包含若干固定步骤和可变步骤。
 * 固定步骤由父类实现，不允许修改；可变步骤由子类重写，以实现不同的行为。
 * 🧱 结构组成
 * 抽象类（Abstract Class）
 * 定义模板方法（templateMethod()）。
 * 实现固定步骤。
 * 声明抽象方法供子类实现。
 * 具体子类（Concrete Class）
 * 实现父类中定义的抽象方法，填充模板中的可变部分。
 * ✅ 优点
 * 复用算法整体结构。
 * 控制子类扩展的范围，符合开闭原则。
 * 统一流程，避免重复代码。
 * ⚠️ 缺点
 * 继承带来的耦合度较高，不灵活。
 * @param <T>
 */
public abstract class CodeFileSaveTemplate<T> {
    //文件保存根目录
    private static final String FILE_SAVE_ROOT_DIR = AppConstant.CODE_OUTPUT_ROOT_DIR;

    /**
     * 模板方法:保存代码的标准流程(使用appID)
     * @param result 代码结果对象
     * @return 保存的目录
     */
    public final File saveCode(T result,Long appId) {
        //1、to validateInput
        validateInput(result);
        //2、establish dir based on appId
        String baseDirPath = buildUniqueDir(appId);
        //3、保存文件，具体实现由子类实现
        saveFiles(result,baseDirPath);
        //4、返回文件目录对象
        return new File(baseDirPath);
    }

    /**
     * 验证输入参数(可用子类覆盖)
     * @param result 代码结果集对象
     */
    protected void validateInput(T result){
        if (result == null) {
            throw  new BusinessException(ErrorCode.SYSTEM_ERROR,"代码结果对象不能为空");
        }
    }

    /**
     *
     * 构建唯一目录路径
     * @return 目录路径
     */
    protected final String buildUniqueDir(Long appId) {
        if (appId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"Id can't be null");
        }
        String codeType = getCodeType().getValue();
        String uniqueDirName = StrUtil.format("{}_{}",codeType, appId);
        String dirPath = FILE_SAVE_ROOT_DIR + File.separator +uniqueDirName;
        FileUtil.mkdir(dirPath);
        return dirPath;
    }

    /**
     * 写入单个文件
     * @param dirPath 目录路径
     * @param fileName 文件名
     * @param content 文件内容
     */
    protected static void writeToFile(String dirPath,String fileName,String content){
        String filePath  = dirPath+File.separator+fileName;
        FileUtil.writeString(content,filePath, StandardCharsets.UTF_8);
    }

    /**
     * 获取代码类型，由子类实现
     * @return 代码类型
     */
    protected abstract CodeGenTypeEnum getCodeType();

    /**
     * 保存文件的具体实现(由子类实现)
     * @param result 保存的文件
     * @param baseDirPath 基础目录路径
     */
    protected abstract void saveFiles(T result,String baseDirPath);

}
