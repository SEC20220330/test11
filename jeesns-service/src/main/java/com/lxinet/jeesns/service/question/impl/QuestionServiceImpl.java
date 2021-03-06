package com.lxinet.jeesns.service.question.impl;

import com.lxinet.jeesns.core.dto.ResultModel;
import com.lxinet.jeesns.core.exception.OpeErrorException;
import com.lxinet.jeesns.core.service.impl.BaseServiceImpl;
import com.lxinet.jeesns.core.utils.HtmlUtil;
import com.lxinet.jeesns.core.utils.PageUtil;
import com.lxinet.jeesns.core.utils.StringUtils;
import com.lxinet.jeesns.core.utils.ValidUtill;
import com.lxinet.jeesns.dao.question.IQuestionDao;
import com.lxinet.jeesns.model.member.Financial;
import com.lxinet.jeesns.model.member.Member;
import com.lxinet.jeesns.model.member.ScoreDetail;
import com.lxinet.jeesns.model.question.Answer;
import com.lxinet.jeesns.model.question.Question;
import com.lxinet.jeesns.model.question.QuestionType;
import com.lxinet.jeesns.service.member.IFinancialService;
import com.lxinet.jeesns.service.member.IMemberService;
import com.lxinet.jeesns.service.member.IScoreDetailService;
import com.lxinet.jeesns.service.question.IAnswerService;
import com.lxinet.jeesns.service.question.IQuestionService;
import com.lxinet.jeesns.service.question.IQuestionTypeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.beans.Transient;
import java.util.Date;
import java.util.List;

/**
 * Created by zchuanzhao on 2018/12/7.
 */
@Service("questionService")
public class QuestionServiceImpl extends BaseServiceImpl<Question> implements IQuestionService {

    @Resource
    private IQuestionDao questionDao;
    @Resource
    private IQuestionTypeService questionTypeService;
    @Resource
    private IMemberService memberService;
    @Resource
    private IFinancialService financialService;
    @Resource
    private IScoreDetailService scoreDetailService;
    @Resource
    private IAnswerService answerService;


    @Override
    public ResultModel<Question> list(Integer typeId, String statusName) {
        Integer status = -2;
        if ("close".equalsIgnoreCase(statusName)){
            status = -1;
        }else if ("unsolved".equalsIgnoreCase(statusName)){
            status = 0;
        }else if ("solved".equalsIgnoreCase(statusName)){
            status = 1;
        }
        List<Question> list = questionDao.list(PageUtil.getPage(), typeId, status);
        ResultModel model = new ResultModel(0,PageUtil.getPage());
        model.setData(list);
        return model;
    }

    @Override
    public Question findById(Integer id) {
        return questionDao.findById(id);
    }

    @Override
    @Transactional
    public boolean save(Question question) {
        ValidUtill.checkParam(question.getMemberId() == null, "??????????????????");
        ValidUtill.checkParam(question.getTypeId() == null, "??????????????????");
        ValidUtill.checkParam(question.getTitle() == null, "??????????????????");
        ValidUtill.checkParam(question.getBonus() < 0, "????????????????????????0");
        QuestionType questionType = questionTypeService.findById(question.getTypeId());
        ValidUtill.checkIsNull(questionType, "?????????????????????");
        Member member = memberService.findById(question.getMemberId());
        if (StringUtils.isEmpty(question.getDescription())) {
            String contentStr = HtmlUtil.delHTMLTag(question.getContent());
            if (contentStr.length() > 200) {
                question.setDescription(contentStr.substring(0, 200));
            } else {
                question.setDescription(contentStr);
            }
        }
        super.save(question);
        if (question.getBonus() > 0){
            if (questionType.getBonusType() == 0){
                //??????
                ValidUtill.checkParam(member.getScore().intValue() < question.getBonus().intValue(), "????????????????????????????????????????????????????????????"+member.getScore());
                memberService.updateScore(-question.getBonus(), member.getId());
                ScoreDetail scoreDetail = new ScoreDetail();
                scoreDetail.setType(1);
                scoreDetail.setMemberId(member.getId());
                scoreDetail.setForeignId(question.getId());
                scoreDetail.setScore(-question.getBonus());
                scoreDetail.setRemark("???????????????" + question.getTitle() + "#" + question.getId());
                scoreDetailService.save(scoreDetail);
            }else if (questionType.getBonusType() == 1){
                //??????
                ValidUtill.checkParam(member.getMoney() < question.getBonus().intValue(), "??????????????????????????????????????????????????????"+member.getMoney());
                memberService.updateMoney(-(double)question.getBonus(), member.getId());
                //??????????????????
                Financial financial = new Financial();
                financial.setBalance(member.getMoney() - question.getBonus());
                financial.setForeignId(question.getId());
                financial.setMemberId(member.getId());
                financial.setMoney((double)question.getBonus());
                financial.setType(1);
                //1???????????????
                financial.setPaymentId(1);
                financial.setRemark("???????????????" + question.getTitle() + "#" + question.getId());
                financial.setOperator(member.getName());
                financialService.save(financial);
            }
        }
        return true;
    }

    @Override
    public boolean update(Member loginMember, Question question) {
        Question findQuestion = findById(question.getId());
        ValidUtill.checkIsNull(findQuestion, "???????????????");
        ValidUtill.checkParam(question.getTitle() == null, "??????????????????");
        if (StringUtils.isEmpty(question.getDescription())) {
            String contentStr = HtmlUtil.delHTMLTag(question.getContent());
            if (contentStr.length() > 200) {
                findQuestion.setDescription(contentStr.substring(0, 200));
            } else {
                findQuestion.setDescription(contentStr);
            }
        }
        findQuestion.setTitle(question.getTitle());
        findQuestion.setContent(question.getContent());
        return super.update(findQuestion);
    }

    @Override
    public boolean delete(Member loginMember, Integer id) {
        Question findQuestion = findById(id);
        ValidUtill.checkParam(findQuestion.getAnswerCount() > 0, "???????????????????????????????????????");
        if(loginMember.getId().intValue() == findQuestion.getMember().getId().intValue() || loginMember.getIsAdmin() > 0){
            return super.deleteById(id);
        }
        throw new OpeErrorException("????????????");
    }

    @Override
    public void close(Member loginMember, Integer id) {
        Question question = findById(id);
        ValidUtill.checkParam(question.getAnswerCount() > 0, "???????????????????????????????????????");
        if(loginMember.getId().intValue() == question.getMember().getId().intValue() || loginMember.getIsAdmin() > 0){
            updateStatus(-1, question);
            //??????????????????????????????
            if (question.getBonus() > 0){
                if (question.getQuestionType().getBonusType() == 0){
                    //??????
                    memberService.updateScore(question.getBonus(), question.getMemberId());
                    ScoreDetail scoreDetail = new ScoreDetail();
                    scoreDetail.setType(1);
                    scoreDetail.setMemberId(question.getMemberId());
                    scoreDetail.setForeignId(question.getId());
                    scoreDetail.setScore(question.getBonus());
                    scoreDetail.setRemark("??????????????????????????????" + question.getTitle() + "#" + question.getId());
                    scoreDetailService.save(scoreDetail);
                }else if (question.getQuestionType().getBonusType() == 1){
                    //??????
                    memberService.updateMoney((double)question.getBonus(), question.getMemberId());
                    Member member = memberService.findById(question.getMemberId());
                    //??????????????????
                    Financial financial = new Financial();
                    financial.setBalance(member.getMoney() + question.getBonus());
                    financial.setForeignId(question.getId());
                    financial.setMemberId(member.getId());
                    financial.setMoney((double)question.getBonus());
                    financial.setType(0);
                    //1???????????????
                    financial.setPaymentId(1);
                    financial.setRemark("??????????????????????????????" + question.getTitle() + "#" + question.getId());
                    financial.setOperator(member.getName());
                    financialService.save(financial);
                }
            }
        }else {
            throw new OpeErrorException("????????????");
        }
    }

    @Override
    @Transactional
    public void bestAnswer(Member loginMember, Integer answerId, Integer id) {
        Question question = findById(id);
        ValidUtill.checkParam(question.getStatus() == 1, "???????????????????????????????????????????????????");
        ValidUtill.checkParam(question.getStatus() == -1, "?????????????????????????????????");
        if(loginMember.getId().intValue() == question.getMember().getId().intValue()){
            Answer answer = answerService.findById(answerId);
            //??????ID??????????????????ID?????????
            ValidUtill.checkParam(answer.getQuestionId().intValue() != id, "????????????");
            ValidUtill.checkParam(answer.getMemberId().intValue() == question.getMemberId().intValue(), "????????????????????????????????????");
            //??????????????????
            setBestAnswer(answerId, id);
            //???????????????????????????????????????
            if (question.getBonus() > 0){
                if (question.getQuestionType().getBonusType() == 0){
                    //??????
                    memberService.updateScore(question.getBonus(), answer.getMemberId());
                    ScoreDetail scoreDetail = new ScoreDetail();
                    scoreDetail.setType(1);
                    scoreDetail.setMemberId(answer.getMemberId());
                    scoreDetail.setForeignId(answer.getId());
                    scoreDetail.setScore(question.getBonus());
                    scoreDetail.setRemark("??????????????????" + question.getTitle() + "#" + question.getId());
                    scoreDetailService.save(scoreDetail);
                }else if (question.getQuestionType().getBonusType() == 1){
                    //??????
                    memberService.updateMoney((double)question.getBonus(), answer.getMemberId());
                    Member member = memberService.findById(answer.getMemberId());
                    //??????????????????
                    Financial financial = new Financial();
                    financial.setBalance(member.getMoney() + question.getBonus());
                    financial.setForeignId(answer.getId());
                    financial.setMemberId(member.getId());
                    financial.setMoney((double)question.getBonus());
                    financial.setType(0);
                    //1???????????????
                    financial.setPaymentId(1);
                    financial.setRemark("??????????????????" + question.getTitle() + "#" + question.getId());
                    financial.setOperator(member.getName());
                    financialService.save(financial);
                }
            }
        }else {
            throw new OpeErrorException("????????????");
        }
    }

    @Override
    public void updateStatus(Integer status, Question question) {
        ValidUtill.checkParam(question.getStatus() != 0, "?????????????????????????????????");
        questionDao.updateStatus(status, question.getId());
    }

    @Override
    public Integer updateAnswerCount(Integer id) {
        return questionDao.updateAnswerCount(id);
    }

    @Override
    public Integer setBestAnswer(Integer answerId, Integer id) {
        return questionDao.setBestAnswer(answerId, id);
    }

    @Override
    public void updateViewCount(Integer id) {
        questionDao.updateViewCount(id);
    }
}
