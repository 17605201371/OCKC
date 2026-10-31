<script setup lang="ts">
import { computed, ref, useId } from 'vue';
import katex from 'katex';
import { buildFormula, formulaStructures, type FormulaKind } from './formulaBuilder';
const id=useId();
const emit=defineEmits<{apply:[latex:string]}>();
const kind=ref<FormulaKind>('fraction'),values=ref({first:'',second:'',arrow:'forward'});
const structure=computed(()=>formulaStructures.find(x=>x.id===kind.value)!);
const result=computed(()=>{try{return {latex:buildFormula(kind.value,values.value),error:''};}catch(e:any){return {latex:'',error:e.message};}});
const preview=computed(()=>{if(!result.value.latex)return '';try{return katex.renderToString(result.value.latex,{displayMode:true,throwOnError:true,trust:false,maxExpand:100,maxSize:20});}catch{return '';}});
const touched=ref(false);
function apply(){touched.value=true;if(result.value.latex&&preview.value)emit('apply',result.value.latex);}
</script>
<template>
  <section class="formula-builder" aria-label="可视化公式结构">
    <label :for="id+'-kind'">公式结构</label><select :id="id+'-kind'" v-model="kind"><option v-for="s in formulaStructures" :key="s.id" :value="s.id">{{s.label}}</option></select>
    <div class="formula-builder-fields"><label v-for="f in structure.fields" :key="f.key">{{f.label}}<input v-model="values[f.key as 'first'|'second']" :placeholder="f.example" maxlength="120" autocomplete="off" @input="touched=true" /></label></div>
    <template v-if="kind==='reaction'"><label :for="id+'-arrow'">反应箭头</label><select :id="id+'-arrow'" v-model="values.arrow"><option value="forward">单向 →</option><option value="reversible">可逆 ⇌</option></select></template>
    <div v-if="preview" class="formula-preview" aria-label="结构排版预览" v-html="preview"></div>
    <p v-else class="small-note">填写结构字段，即时查看排版。复杂嵌套可应用后在 LaTeX 中继续编辑。</p>
    <p v-if="touched && result.error" role="alert" class="writer-field-error">{{result.error}}</p>
    <p class="small-note">反应式自动将化学式中的数字排为下标；不做配平、单位检查或学科正确性判断。</p>
    <button type="button" class="btn secondary" @click="apply">应用结构到公式</button>
  </section>
</template>
