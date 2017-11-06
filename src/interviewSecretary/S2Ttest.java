package interviewSecretary;

import java.util.HashMap;

import org.apache.log4j.PropertyConfigurator;

import com.alibaba.fastjson.JSON;
import com.iflytek.msp.cpdb.lfasr.client.LfasrClientImp;
import com.iflytek.msp.cpdb.lfasr.exception.LfasrException;
import com.iflytek.msp.cpdb.lfasr.model.LfasrType;
import com.iflytek.msp.cpdb.lfasr.model.Message;
import com.iflytek.msp.cpdb.lfasr.model.ProgressStatus;

public class S2Ttest {
	// 原始音频存放地址
			private String input_file = "";
			/*
			 * 转写类型选择：标准版和电话版分别为：
			 * LfasrType.LFASR_STANDARD_RECORDED_AUDIO 和 LfasrType.LFASR_TELEPHONY_RECORDED_AUDIO
			 * */
			private LfasrType type = LfasrType.LFASR_STANDARD_RECORDED_AUDIO;
			// 等待时长（秒）
			private int sleepSecond = 20;
		
		S2Ttest(String default_inpath_value){
			input_file = default_inpath_value.replace("\\", "\\\\");
		}
	public String start() {
		// 加载配置文件
		//PropertyConfigurator.configure("log4j.properties");
		
		// 初始化LFASR实例
		LfasrClientImp lc = null;
		try {
			lc = LfasrClientImp.initLfasrClient();
		} catch (LfasrException e) {
			// 初始化异常，解析异常描述信息
			Message initMsg = JSON.parseObject(e.getMessage(), Message.class);
			System.out.println("ecode=" + initMsg.getErr_no());
			System.out.println("failed=" + initMsg.getFailed());
			return "failed";
		}
				
		// 获取上传任务ID
		String task_id = "";
		HashMap<String, String> params = new HashMap<>();
		params.put("has_participle", "true");
		try {
			// 上传音频文件
			Message uploadMsg = lc.lfasrUpload(input_file, type, params);
			
			// 判断返回值
			int ok = uploadMsg.getOk();
			if (ok == 0) {
				// 创建任务成功
				task_id = uploadMsg.getData();
				System.out.println("task_id=" + task_id);
			} else {
				// 创建任务失败-服务端异常
				System.out.println("ecode=" + uploadMsg.getErr_no());
				System.out.println("failed=" + uploadMsg.getFailed());
			}
		} catch (LfasrException e) {
			// 上传异常，解析异常描述信息
			Message uploadMsg = JSON.parseObject(e.getMessage(), Message.class);
			System.out.println("ecode=" + uploadMsg.getErr_no());
			System.out.println("failed=" + uploadMsg.getFailed());	
			return "failed";
		}
				
		// 循环等待音频处理结果
		while (true) {
			try {
				// 睡眠1min。另外一个方案是让用户尝试多次获取，第一次假设等1分钟，获取成功后break；失败的话增加到2分钟再获取，获取成功后break；再失败的话加到4分钟；8分钟；……
				Thread.sleep(sleepSecond * 1000);
				System.out.println("waiting ...");
			} catch (InterruptedException e) {
				return "failed";
			}
			try {
				// 获取处理进度
				Message progressMsg = lc.lfasrGetProgress(task_id);
						
				// 如果返回状态不等于0，则任务失败
				if (progressMsg.getOk() != 0) {
					System.out.println("task was fail. task_id:" + task_id);
					System.out.println("ecode=" + progressMsg.getErr_no());
					System.out.println("failed=" + progressMsg.getFailed());
					
					// 服务端处理异常-服务端内部有重试机制（不排查极端无法恢复的任务）
					// 客户端可根据实际情况选择：
					// 1. 客户端循环重试获取进度
					// 2. 退出程序，反馈问题
					continue;
				} else {
					ProgressStatus progressStatus = JSON.parseObject(progressMsg.getData(), ProgressStatus.class);
					if (progressStatus.getStatus() == 9) {
						// 处理完成
						System.out.println("task was completed. task_id:" + task_id);
						break;	
					} else {
						// 未处理完成
						System.out.println("task was incomplete. task_id:" + task_id + ", status:" + progressStatus.getDesc());
						continue;
					}
				}
			} catch (LfasrException e) {
				// 获取进度异常处理，根据返回信息排查问题后，再次进行获取
				Message progressMsg = JSON.parseObject(e.getMessage(), Message.class);
				System.out.println("ecode=" + progressMsg.getErr_no());
				System.out.println("failed=" + progressMsg.getFailed());
				return "failed";
			}
		}

		// 获取任务结果
		try {
			Message resultMsg = lc.lfasrGetResult(task_id);
			//System.out.println(resultMsg.getData());	
			// 如果返回状态等于0，则任务处理成功
			if (resultMsg.getOk() == 0) {
				// 打印转写结果
				//System.out.println(resultMsg.getData());
				return resultMsg.getData();
			} else {
				// 转写失败，根据失败信息进行处理
				System.out.println("ecode=" + resultMsg.getErr_no());
				System.out.println("failed=" + resultMsg.getFailed());
				return "failed";
			}
		} catch (LfasrException e) {
			// 获取结果异常处理，解析异常描述信息
			Message resultMsg = JSON.parseObject(e.getMessage(), Message.class);
			System.out.println("ecode=" + resultMsg.getErr_no());
			System.out.println("failed=" + resultMsg.getFailed());
			return "failed";
		}
	}
}