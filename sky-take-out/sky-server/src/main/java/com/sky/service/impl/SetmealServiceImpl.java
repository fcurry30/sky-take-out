package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.service.SetmealService;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class SetmealServiceImpl implements SetmealService {

    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private DishService dishService;

    /**
     * 新增套餐管理，要保存套餐和菜品的关联关系
     */
    @Transactional //两张表涉及
    public void saveWithDish(SetmealDTO setmealDTO){
        //复制属性至setmeal里面
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO,setmeal);
        //利用Mapper插入数据
        setmealMapper.insert(setmeal);
        //接下来要关联套餐和菜品
        Long id = setmeal.getId();//利用刚才主键回填获得的id
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        for(SetmealDish setmealDish : setmealDishes){
            setmealDish.setSetmealId(id);
        }
        setmealDishMapper.insertBatch(setmealDishes);
        return ;
    }

    /**
     * 分页查询套餐
     * @param setmealPageQueryDTO
     * @return
     */
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO){
        int num = setmealPageQueryDTO.getPage();
        int size = setmealPageQueryDTO.getPageSize();
        PageHelper.startPage(num, size);
        Page<SetmealVO> page = setmealMapper.pageQuery(setmealPageQueryDTO);
        return new PageResult(page.getTotal(),page.getResult());
    }

    /**
     * 根据ids删除套餐
     */
    public void deleteByIds(List<Long> ids){
        //一次可以删除一个套餐，也可以删除多个套餐
        //起售中的套餐不能删除。
        for(Long id : ids){
            Setmeal setmeal = setmealMapper.getById(id);
            if(setmeal.getStatus() == StatusConstant.ENABLE){
                throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
            }
        }
        for(Long id : ids){
            setmealMapper.deleteById(id);
            setmealDishMapper.deleteById(id);
        }
    }

    /**
     * 根据id查询套餐信息（要求包括套餐的信息和菜品的信息）
     */
    public SetmealVO getById(Long id){
        Setmeal setmeal = setmealMapper.getById(id);
        List<SetmealDish> list = setmealDishMapper.getBySetmealId(id);
        SetmealVO setmealVO = new SetmealVO();
        BeanUtils.copyProperties(setmeal,setmealVO);
        setmealVO.setSetmealDishes(list);
        return setmealVO;
    }

    /**
     * 修改套餐信息
     */
    @Transactional
    public void update(SetmealDTO setmealDTO){
        //分别修改两个表里面的数据
        //获取setmeal实体类，然后update数据
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO,setmeal);
        setmealMapper.update(setmeal);
        //删除setmealdish表里菜品关系
        Long setmealId = setmealDTO.getId();
        setmealDishMapper.deleteById(setmealId);
        //获取要插入的新的菜品关联数据
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        for(SetmealDish s : setmealDishes){
            s.setSetmealId(setmealId);
        }
        setmealDishMapper.insertBatch(setmealDishes);
    }
    public void startOrStop(Integer status,Long id){
        //停售套餐不等于停售菜品，因为里面的菜品可能被其他套餐关联
        //但起售套餐要注意是否存在停售菜品
        if(status == StatusConstant.ENABLE){
            //需要获取菜品的具体信息：STATUS
            List<Dish> dishes = dishMapper.getBySetmealId(id);
            for(Dish dish : dishes){
                if(dish.getStatus() == StatusConstant.DISABLE){
                    throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ENABLE_FAILED);
                }
            }
        }
        //要根据id把status信息存储进去。
        Setmeal setmeal = Setmeal.builder()
                .id(id)
                .status(status)
                .build();
        setmealMapper.update(setmeal);
    }

    /**
     * 条件查询
     * @param setmeal
     * @return
     */
    public List<Setmeal> list(Setmeal setmeal) {
        List<Setmeal> list = setmealMapper.list(setmeal);
        return list;
    }

    /**
     * 根据id查询菜品选项
     * @param id
     * @return
     */
    public List<DishItemVO> getDishItemById(Long id) {
        return setmealMapper.getDishItemBySetmealId(id);
    }
}

